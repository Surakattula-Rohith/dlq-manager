package com.dlqmanager.service;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.dto.BulkReplayRequestDto;
import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.AlertStatus;
import com.dlqmanager.model.enums.AlertType;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.repository.AlertEventRepository;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.repository.DlqTopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An alert from start to finish, with a real database and Kafka:
 * raised once, kept while the problem lasts, resolved when the messages are replayed.
 */
class AlertLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AlertEvaluatorService alertEvaluatorService;

    @Autowired
    private AlertRuleService alertRuleService;

    @Autowired
    private ReplayService replayService;

    @Autowired
    private DlqTopicRepository dlqTopicRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @Test
    void aLastingProblemIsOneAlertUntilItIsFixed() throws Exception {
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        createTopic(source, 1);
        createTopic(dlq, 1);
        produce(dlq, 0, 3, Map.of("X-Error-Message", "DB Connection Timeout"));
        DlqTopic topic = register(dlq, source);

        // 2 or more pending messages is a problem; cooldown 0 = "remind on every check"
        AlertRule rule = new AlertRule();
        rule.setName(uniqueTopic("backlog"));
        rule.setDlqTopic(topic);
        rule.setAlertType(AlertType.THRESHOLD);
        rule.setThreshold(2L);
        rule.setCooldownMinutes(0);
        rule.setEnabled(true);
        UUID ruleId = alertRuleRepository.save(rule).getId();

        // The problem is seen three times in a row: still one alert, not three
        alertEvaluatorService.evaluateAlerts();
        alertEvaluatorService.evaluateAlerts();
        alertEvaluatorService.evaluateAlerts();

        List<AlertEvent> open = alertEventRepository.findByAlertRuleIdAndResolvedAtIsNullOrderByTriggeredAtDesc(ruleId);
        assertThat(open).hasSize(1);
        assertThat(open.get(0).getStatus()).isEqualTo(AlertStatus.FIRING);
        assertThat(open.get(0).getMessageCount()).isEqualTo(3);
        assertThat(alertRuleService.countFiringAlerts()).isPositive();

        // Replaying the messages fixes it: the alert is resolved and no longer counts as firing
        BulkReplayRequestDto replay = new BulkReplayRequestDto();
        replay.setDlqTopicId(topic.getId());
        replay.setInitiatedBy("it-test");
        replay.setMessages(List.of(
                new BulkReplayRequestDto.MessageIdentifier(0L, 0),
                new BulkReplayRequestDto.MessageIdentifier(1L, 0),
                new BulkReplayRequestDto.MessageIdentifier(2L, 0)));
        replayService.bulkReplayMessages(replay);
        alertEvaluatorService.evaluateAlerts();

        assertThat(alertEventRepository.findByAlertRuleIdAndResolvedAtIsNullOrderByTriggeredAtDesc(ruleId)).isEmpty();
        assertThat(alertEventRepository.findAll().stream()
                .filter(event -> event.getAlertRule().getId().equals(ruleId)))
                .singleElement()
                .satisfies(event -> assertThat(event.getResolvedAt()).isNotNull());

        // New failures later are a new problem: a second alert, the first stays resolved in the history
        produce(dlq, 0, 2, Map.of("X-Error-Message", "DB Connection Timeout"));
        alertEvaluatorService.evaluateAlerts();

        assertThat(alertEventRepository.findByAlertRuleIdAndResolvedAtIsNullOrderByTriggeredAtDesc(ruleId)).hasSize(1);
        assertThat(alertEventRepository.findAll().stream()
                .filter(event -> event.getAlertRule().getId().equals(ruleId))).hasSize(2);
    }

    private DlqTopic register(String dlqTopicName, String sourceTopic) {
        DlqTopic topic = new DlqTopic();
        topic.setDlqTopicName(dlqTopicName);
        topic.setSourceTopic(sourceTopic);
        topic.setDetectionType(DetectionType.MANUAL);
        topic.setStatus(DlqStatus.ACTIVE);
        return dlqTopicRepository.save(topic);
    }
}
