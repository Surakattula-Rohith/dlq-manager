package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.DlqCountSample;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.AlertStatus;
import com.dlqmanager.model.enums.AlertType;
import com.dlqmanager.repository.AlertEventRepository;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.repository.DlqCountSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEvaluatorServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertEventRepository alertEventRepository;
    @Mock
    private DlqCountSampleRepository dlqCountSampleRepository;
    @Mock
    private DlqBrowserService dlqBrowserService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AlertEvaluatorService alertEvaluatorService;

    private DlqTopic topic;

    @BeforeEach
    void setUp() {
        topic = new DlqTopic();
        topic.setId(UUID.randomUUID());
        topic.setDlqTopicName("orders-dlq");
    }

    // --- Threshold rules ---

    @Test
    void thresholdRuleFiresWhenPendingReachesThreshold() {
        NotificationChannel slack = new NotificationChannel();
        AlertRule rule = thresholdRule(40);
        rule.setNotificationChannel(slack);
        givenRules(rule);
        givenCounts(56, 0, 58);

        alertEvaluatorService.evaluateAlerts();

        AlertEvent event = savedEvent();
        assertThat(event.getStatus()).isEqualTo(AlertStatus.FIRING);
        assertThat(event.getMessageCount()).isEqualTo(56L);
        assertThat(rule.getLastFiredAt()).isNotNull();
        verify(notificationService).sendNotification(slack, rule, 56L);
    }

    @Test
    void thresholdRuleUsesPendingCountNotTotal() {
        // 60 messages in the DLQ, but 30 were already replayed -> only 30 pending
        givenRules(thresholdRule(40));
        givenCounts(60, 30, 60);

        alertEvaluatorService.evaluateAlerts();

        verify(alertEventRepository, never()).save(any());
    }

    @Test
    void snoozedRuleDoesNotFire() {
        AlertRule rule = thresholdRule(40);
        givenRules(rule);
        givenCounts(56, 0, 58);
        when(alertEventRepository.existsByAlertRuleIdAndStatusAndSnoozedUntilAfter(
                eq(rule.getId()), eq(AlertStatus.SNOOZED), any())).thenReturn(true);

        alertEvaluatorService.evaluateAlerts();

        verify(alertEventRepository, never()).save(any());
        verify(notificationService, never()).sendNotification(any(), any(), anyLong());
    }

    @Test
    void ruleInCooldownDoesNotFireAgain() {
        AlertRule rule = thresholdRule(40);
        rule.setCooldownMinutes(30);
        rule.setLastFiredAt(LocalDateTime.now().minusMinutes(5));
        givenRules(rule);
        givenCounts(56, 0, 58);

        alertEvaluatorService.evaluateAlerts();

        verify(alertEventRepository, never()).save(any());
    }

    @Test
    void ruleFiresAgainOnceCooldownHasPassed() {
        AlertRule rule = thresholdRule(40);
        rule.setCooldownMinutes(30);
        rule.setLastFiredAt(LocalDateTime.now().minusMinutes(31));
        givenRules(rule);
        givenCounts(56, 0, 58);

        alertEvaluatorService.evaluateAlerts();

        assertThat(savedEvent().getStatus()).isEqualTo(AlertStatus.FIRING);
    }

    // --- Time-window rules ---

    @Test
    void timeWindowRuleFiresOnNewArrivalsSinceWindowStart() {
        NotificationChannel slack = new NotificationChannel();
        AlertRule rule = timeWindowRule(3, 5);
        rule.setNotificationChannel(slack);
        givenRules(rule);
        givenCounts(56, 0, 58);
        givenBaselineEndOffsetSum(53);

        alertEvaluatorService.evaluateAlerts();

        savedEvent();
        // Slack gets the number of NEW messages in the window (58 - 53), not the pending count
        verify(notificationService).sendNotification(slack, rule, 5L);
    }

    @Test
    void timeWindowRuleStaysQuietWhenFewNewMessages() {
        givenRules(timeWindowRule(3, 5));
        givenCounts(56, 0, 58);
        givenBaselineEndOffsetSum(57);

        alertEvaluatorService.evaluateAlerts();

        verify(alertEventRepository, never()).save(any());
    }

    @Test
    void timeWindowRuleStaysQuietWithoutHistory() {
        givenRules(timeWindowRule(3, 5));
        givenCounts(56, 0, 58);
        // no baseline sample stored yet -> Optional.empty() by default

        alertEvaluatorService.evaluateAlerts();

        verify(alertEventRepository, never()).save(any());
    }

    // --- Housekeeping ---

    @Test
    void expiredSnoozeGoesBackToFiring() {
        AlertEvent snoozed = new AlertEvent();
        snoozed.setStatus(AlertStatus.SNOOZED);
        snoozed.setSnoozedUntil(LocalDateTime.now().minusMinutes(1));
        when(alertEventRepository.findByStatusAndSnoozedUntilBefore(eq(AlertStatus.SNOOZED), any()))
                .thenReturn(List.of(snoozed));

        alertEvaluatorService.evaluateAlerts();

        assertThat(snoozed.getStatus()).isEqualTo(AlertStatus.FIRING);
        verify(alertEventRepository).saveAll(List.of(snoozed));
    }

    @Test
    void readsKafkaOncePerTopicEvenWithSeveralRules() {
        givenRules(thresholdRule(1_000), timeWindowRule(1_000, 5));
        givenCounts(56, 0, 58);

        alertEvaluatorService.evaluateAlerts();

        verify(dlqBrowserService, times(1)).getMessageCounts(topic.getId());
        verify(dlqCountSampleRepository, times(1)).save(any(DlqCountSample.class));
    }

    // --- Helpers ---

    private AlertRule thresholdRule(long threshold) {
        AlertRule rule = new AlertRule();
        rule.setId(UUID.randomUUID());
        rule.setName("threshold-" + threshold);
        rule.setDlqTopic(topic);
        rule.setAlertType(AlertType.THRESHOLD);
        rule.setThreshold(threshold);
        rule.setCooldownMinutes(30);
        return rule;
    }

    private AlertRule timeWindowRule(long threshold, int windowMinutes) {
        AlertRule rule = thresholdRule(threshold);
        rule.setName("window-" + threshold);
        rule.setAlertType(AlertType.TIME_WINDOW);
        rule.setWindowMinutes(windowMinutes);
        return rule;
    }

    private void givenRules(AlertRule... rules) {
        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rules));
    }

    private void givenCounts(long total, long replayed, long endOffsetSum) {
        when(dlqBrowserService.getMessageCounts(topic.getId()))
                .thenReturn(new DlqBrowserService.MessageCounts(total, replayed, total - replayed, endOffsetSum));
    }

    private void givenBaselineEndOffsetSum(long endOffsetSum) {
        DlqCountSample baseline = new DlqCountSample();
        baseline.setEndOffsetSum(endOffsetSum);
        when(dlqCountSampleRepository.findFirstByDlqTopicIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(eq(topic.getId()), any()))
                .thenReturn(Optional.of(baseline));
    }

    private AlertEvent savedEvent() {
        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventRepository).save(captor.capture());
        return captor.getValue();
    }
}
