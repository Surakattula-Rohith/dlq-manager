package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.DlqCountSample;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.AlertStatus;
import com.dlqmanager.model.enums.AlertType;
import com.dlqmanager.repository.AlertEventRepository;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.repository.DlqCountSampleRepository;
import com.dlqmanager.repository.DlqTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Alert Evaluator
 *
 * Runs every 60 seconds:
 * 1. Ends snoozes whose time is up (alert goes back to FIRING)
 * 2. Takes one count sample per active DLQ topic, and per topic an enabled rule watches
 *    (used by time-window rules and by the trend chart on each DLQ page)
 * 3. Evaluates each enabled rule
 *    - THRESHOLD:   pending messages >= threshold
 *                   (pending = not replayed yet, so replaying the DLQ clears the alert)
 *    - TIME_WINDOW: new messages in the last X minutes >= threshold
 *                   (compared against the sample taken at the start of the window)
 *    A rule has at most one open alert: it is raised when the problem starts, reminded
 *    once per cooldown while nobody has acknowledged or snoozed it, and resolved when
 *    the problem goes away (see evaluateRule).
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
    private final DlqTopicRepository dlqTopicRepository;

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    @Transactional
    public void evaluateAlerts() {
        LocalDateTime now = LocalDateTime.now();

        endExpiredSnoozes(now);

        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();
        Map<UUID, DlqBrowserService.MessageCounts> countsByTopic = sampleTopics(topicsToSample(rules), now);

        if (!rules.isEmpty()) {
            log.debug("Evaluating {} alert rule(s)", rules.size());

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
     * Every active DLQ topic (for the trend chart), plus any topic an enabled rule watches
     * (a rule can point at a paused topic). Each topic once, however many rules watch it.
     */
    private Collection<DlqTopic> topicsToSample(List<AlertRule> rules) {
        Map<UUID, DlqTopic> topics = new LinkedHashMap<>();
        for (DlqTopic topic : dlqTopicRepository.findAllActive()) {
            topics.put(topic.getId(), topic);
        }
        for (AlertRule rule : rules) {
            topics.putIfAbsent(rule.getDlqTopic().getId(), rule.getDlqTopic());
        }
        return topics.values();
    }

    /**
     * Read counts once per topic and store a sample.
     *
     * If Kafka doesn't answer, the rest of the topics are skipped until the next minute:
     * each would wait for the same timeout and hold up the alerts that do work.
     */
    private Map<UUID, DlqBrowserService.MessageCounts> sampleTopics(Collection<DlqTopic> topics, LocalDateTime now) {
        Map<UUID, DlqBrowserService.MessageCounts> countsByTopic = new HashMap<>();

        for (DlqTopic topic : topics) {
            UUID topicId = topic.getId();
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
                log.warn("Could not get message count for topic '{}': {}", topic.getDlqTopicName(), e.getMessage());
                if (isKafkaTimeout(e)) {
                    log.warn("Kafka is not answering - skipping the other topics until the next check");
                    break;
                }
            }
        }

        return countsByTopic;
    }

    private static boolean isKafkaTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.apache.kafka.common.errors.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * One rule, one open alert.
     *
     * - Problem there, no open alert: raise one (unless the rule is snoozed or in cooldown)
     * - Problem still there, alert open: keep that alert. While nobody has acknowledged or
     *   snoozed it, remind the channel once per cooldown. Never raise a second one.
     * - Problem gone: mark the open alert resolved. If it comes back, a new alert is raised.
     *
     * Raising a new alert every cooldown (the earlier behaviour) buried a lasting problem
     * under one "firing" alert per hour.
     */
    private void evaluateRule(AlertRule rule, DlqBrowserService.MessageCounts counts, LocalDateTime now) {
        long pendingCount = counts.pending();

        // Update snapshot (shown for debugging / future UI)
        rule.setLastCheckedCount(pendingCount);
        rule.setLastCheckedAt(now);

        Long observedValue = observedValue(rule, counts, now);
        boolean problem = observedValue != null && observedValue >= rule.getThreshold();
        Optional<AlertEvent> openAlert = openAlert(rule, now);

        if (!problem) {
            openAlert.ifPresent(alert -> {
                alert.setResolvedAt(now);
                alertEventRepository.save(alert);
                log.info("Alert resolved: rule='{}' topic='{}'", rule.getName(), rule.getDlqTopic().getDlqTopicName());
            });
            alertRuleRepository.save(rule);
            return;
        }

        boolean inCooldown = rule.getLastFiredAt() != null
                && Duration.between(rule.getLastFiredAt(), now).toMinutes() < rule.getCooldownMinutes();

        if (openAlert.isPresent()) {
            AlertEvent alert = openAlert.get();
            alert.setMessageCount(pendingCount);
            alertEventRepository.save(alert);

            // Acknowledged or snoozed: someone knows. Still firing: remind, but only once per cooldown.
            if (alert.getStatus() == AlertStatus.FIRING && !inCooldown) {
                log.info("Alert still firing, reminding: rule='{}' value={}", rule.getName(), observedValue);
                rule.setLastFiredAt(now);
                notifyChannel(rule, observedValue);
            }
            alertRuleRepository.save(rule);
            return;
        }

        // No open alert. A snooze that is still running means "not now" for this rule,
        // and the cooldown keeps a problem that comes and goes from raising an alert a minute.
        boolean snoozed = alertEventRepository.existsByAlertRuleIdAndStatusAndSnoozedUntilAfter(
                rule.getId(), AlertStatus.SNOOZED, now);
        if (snoozed || inCooldown) {
            log.debug("Rule '{}' is {}", rule.getName(), snoozed ? "snoozed" : "in cooldown");
            alertRuleRepository.save(rule);
            return;
        }

        log.info("Alert fired: rule='{}' topic='{}' value={} threshold={}",
                rule.getName(), rule.getDlqTopic().getDlqTopicName(), observedValue, rule.getThreshold());

        AlertEvent event = new AlertEvent();
        event.setAlertRule(rule);
        event.setStatus(AlertStatus.FIRING);
        event.setMessageCount(pendingCount);
        event.setTriggeredAt(now);
        alertEventRepository.save(event);

        rule.setLastFiredAt(now);
        notifyChannel(rule, observedValue);
        alertRuleRepository.save(rule);
    }

    /**
     * The number the rule compares with its threshold
     * - THRESHOLD:   pending messages
     * - TIME_WINDOW: new messages since the start of the window
     *
     * @return null if the rule can't be evaluated (a time-window rule without a window)
     */
    private Long observedValue(AlertRule rule, DlqBrowserService.MessageCounts counts, LocalDateTime now) {
        if (rule.getAlertType() == AlertType.THRESHOLD) {
            return counts.pending();
        }
        if (rule.getAlertType() == AlertType.TIME_WINDOW) {
            if (rule.getWindowMinutes() == null || rule.getWindowMinutes() <= 0) {
                log.warn("Rule '{}' is a time-window rule without a window, skipping", rule.getName());
                return null;
            }
            LocalDateTime windowStart = now.minusMinutes(rule.getWindowMinutes());
            long newMessages = dlqCountSampleRepository
                    .findFirstByDlqTopicIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(rule.getDlqTopic().getId(), windowStart)
                    .map(baseline -> counts.endOffsetSum() - baseline.getEndOffsetSum())
                    .orElse(0L);
            return Math.max(0, newMessages);
        }
        return null;
    }

    /**
     * The rule's open (not yet resolved) alert, if any.
     *
     * Data from before "one open alert per rule" can hold several: the newest stays open
     * and the older duplicates are resolved here, so they stop counting as active.
     */
    private Optional<AlertEvent> openAlert(AlertRule rule, LocalDateTime now) {
        List<AlertEvent> open = alertEventRepository.findByAlertRuleIdAndResolvedAtIsNullOrderByTriggeredAtDesc(rule.getId());
        if (open.isEmpty()) {
            return Optional.empty();
        }
        for (AlertEvent duplicate : open.subList(1, open.size())) {
            duplicate.setResolvedAt(now);
            alertEventRepository.save(duplicate);
        }
        return Optional.of(open.get(0));
    }

    private void notifyChannel(AlertRule rule, long observedValue) {
        if (rule.getNotificationChannel() != null) {
            notificationService.sendNotification(rule.getNotificationChannel(), rule, observedValue);
        }
    }
}
