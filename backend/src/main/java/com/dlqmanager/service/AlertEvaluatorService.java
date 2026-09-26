package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.DlqCountSample;
import com.dlqmanager.model.enums.AlertStatus;
import com.dlqmanager.model.enums.AlertType;
import com.dlqmanager.repository.AlertEventRepository;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.repository.DlqCountSampleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alert Evaluator
 *
 * Runs every 60 seconds:
 * 1. Ends snoozes whose time is up (alert goes back to FIRING)
 * 2. Takes one count sample per DLQ topic that has an enabled rule
 * 3. Evaluates each enabled rule
 *    - THRESHOLD:   pending messages >= threshold
 *                   (pending = not replayed yet, so replaying the DLQ clears the alert)
 *    - TIME_WINDOW: new messages in the last X minutes >= threshold
 *                   (compared against the sample taken at the start of the window)
 *    Skipped while the rule is in cooldown or has a snoozed alert.
 * 4. Deletes count samples older than 7 days
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEvaluatorService {

    private static final int SAMPLE_RETENTION_DAYS = 7;

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final DlqCountSampleRepository dlqCountSampleRepository;
    private final DlqBrowserService dlqBrowserService;
    private final NotificationService notificationService;

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    @Transactional
    public void evaluateAlerts() {
        LocalDateTime now = LocalDateTime.now();

        endExpiredSnoozes(now);

        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();
        if (!rules.isEmpty()) {
            log.debug("Evaluating {} alert rule(s)", rules.size());

            Map<UUID, DlqBrowserService.MessageCounts> countsByTopic = sampleTopics(rules, now);

            for (AlertRule rule : rules) {
                try {
                    DlqBrowserService.MessageCounts counts = countsByTopic.get(rule.getDlqTopic().getId());
                    if (counts != null) {
                        evaluateRule(rule, counts, now);
                    }
                } catch (Exception e) {
                    log.error("Error evaluating alert rule '{}': {}", rule.getName(), e.getMessage());
                }
            }
        }

        int pruned = dlqCountSampleRepository.deleteOlderThan(now.minusDays(SAMPLE_RETENTION_DAYS));
        if (pruned > 0) {
            log.debug("Pruned {} old count sample(s)", pruned);
        }
    }

    /**
     * A snooze only lasts until snoozedUntil. After that the alert is unresolved again.
     */
    private void endExpiredSnoozes(LocalDateTime now) {
        List<AlertEvent> expired = alertEventRepository.findByStatusAndSnoozedUntilBefore(AlertStatus.SNOOZED, now);
        for (AlertEvent event : expired) {
            event.setStatus(AlertStatus.FIRING);
            log.info("Snooze ended for alert event {}", event.getId());
        }
        if (!expired.isEmpty()) {
            alertEventRepository.saveAll(expired);
        }
    }

    /**
     * Read counts once per topic (several rules can watch the same topic)
     * and store a sample for time-window rules.
     */
    private Map<UUID, DlqBrowserService.MessageCounts> sampleTopics(List<AlertRule> rules, LocalDateTime now) {
        Map<UUID, DlqBrowserService.MessageCounts> countsByTopic = new HashMap<>();

        for (AlertRule rule : rules) {
            UUID topicId = rule.getDlqTopic().getId();
            if (countsByTopic.containsKey(topicId)) {
                continue;
            }
            try {
                DlqBrowserService.MessageCounts counts = dlqBrowserService.getMessageCounts(topicId);
                countsByTopic.put(topicId, counts);

                DlqCountSample sample = new DlqCountSample();
                sample.setDlqTopicId(topicId);
                sample.setPendingCount(counts.pending());
                sample.setEndOffsetSum(counts.endOffsetSum());
                sample.setSampledAt(now);
                dlqCountSampleRepository.save(sample);

            } catch (Exception e) {
                log.warn("Could not get message count for topic '{}': {}",
                        rule.getDlqTopic().getDlqTopicName(), e.getMessage());
            }
        }

        return countsByTopic;
    }

    private void evaluateRule(AlertRule rule, DlqBrowserService.MessageCounts counts, LocalDateTime now) {
        long pendingCount = counts.pending();

        // Update snapshot (shown for debugging / future UI)
        rule.setLastCheckedCount(pendingCount);
        rule.setLastCheckedAt(now);

        // Respect snooze: someone said "not now" for this rule
        if (alertEventRepository.existsByAlertRuleIdAndStatusAndSnoozedUntilAfter(rule.getId(), AlertStatus.SNOOZED, now)) {
            log.debug("Rule '{}' is snoozed", rule.getName());
            alertRuleRepository.save(rule);
            return;
        }

        // Respect cooldown
        if (rule.getLastFiredAt() != null) {
            long minutesSinceFired = Duration.between(rule.getLastFiredAt(), now).toMinutes();
            if (minutesSinceFired < rule.getCooldownMinutes()) {
                log.debug("Rule '{}' is in cooldown ({}/{} min)", rule.getName(), minutesSinceFired, rule.getCooldownMinutes());
                alertRuleRepository.save(rule);
                return;
            }
        }

        boolean shouldFire = false;
        long observedValue = pendingCount;

        if (rule.getAlertType() == AlertType.THRESHOLD) {
            shouldFire = pendingCount >= rule.getThreshold();

        } else if (rule.getAlertType() == AlertType.TIME_WINDOW) {
            if (rule.getWindowMinutes() == null || rule.getWindowMinutes() <= 0) {
                log.warn("Rule '{}' is a time-window rule without a window, skipping", rule.getName());
            } else {
                LocalDateTime windowStart = now.minusMinutes(rule.getWindowMinutes());
                long newMessages = dlqCountSampleRepository
                        .findFirstByDlqTopicIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(rule.getDlqTopic().getId(), windowStart)
                        .map(baseline -> counts.endOffsetSum() - baseline.getEndOffsetSum())
                        .orElse(0L);

                observedValue = Math.max(0, newMessages);
                shouldFire = observedValue >= rule.getThreshold();
            }
        }

        if (shouldFire) {
            log.info("Alert fired: rule='{}' topic='{}' value={} threshold={}",
                    rule.getName(), rule.getDlqTopic().getDlqTopicName(), observedValue, rule.getThreshold());

            AlertEvent event = new AlertEvent();
            event.setAlertRule(rule);
            event.setStatus(AlertStatus.FIRING);
            event.setMessageCount(pendingCount);
            event.setTriggeredAt(now);
            alertEventRepository.save(event);

            rule.setLastFiredAt(now);

            if (rule.getNotificationChannel() != null) {
                notificationService.sendNotification(rule.getNotificationChannel(), rule, observedValue);
            }
        }

        alertRuleRepository.save(rule);
    }
}
