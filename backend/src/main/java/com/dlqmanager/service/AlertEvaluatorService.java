package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.enums.AlertStatus;
import com.dlqmanager.model.enums.AlertType;
import com.dlqmanager.repository.AlertEventRepository;
import com.dlqmanager.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEvaluatorService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final DlqBrowserService dlqBrowserService;
    private final NotificationService notificationService;

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    @Transactional
    public void evaluateAlerts() {
        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();
        if (rules.isEmpty()) return;

        log.debug("Evaluating {} alert rule(s)", rules.size());
        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.error("Error evaluating alert rule '{}': {}", rule.getName(), e.getMessage());
            }
        }
    }

    private void evaluateRule(AlertRule rule) {
        // Respect cooldown
        if (rule.getLastFiredAt() != null) {
            long minutesSinceFired = Duration.between(rule.getLastFiredAt(), LocalDateTime.now()).toMinutes();
            if (minutesSinceFired < rule.getCooldownMinutes()) {
                log.debug("Rule '{}' is in cooldown ({}/{} min)", rule.getName(), minutesSinceFired, rule.getCooldownMinutes());
                return;
            }
        }

        long currentCount;
        try {
            currentCount = dlqBrowserService.getMessageCount(rule.getDlqTopic().getId());
        } catch (Exception e) {
            log.warn("Could not get message count for topic '{}': {}", rule.getDlqTopic().getDlqTopicName(), e.getMessage());
            return;
        }

        boolean shouldFire = false;

        if (rule.getAlertType() == AlertType.THRESHOLD) {
            shouldFire = currentCount >= rule.getThreshold();

        } else if (rule.getAlertType() == AlertType.TIME_WINDOW) {
            if (rule.getLastCheckedCount() != null && rule.getLastCheckedAt() != null) {
                LocalDateTime windowStart = LocalDateTime.now().minusMinutes(rule.getWindowMinutes());
                if (rule.getLastCheckedAt().isAfter(windowStart)) {
                    long increase = currentCount - rule.getLastCheckedCount();
                    shouldFire = increase >= rule.getThreshold();
                }
            }
        }

        // Update snapshot
        rule.setLastCheckedCount(currentCount);
        rule.setLastCheckedAt(LocalDateTime.now());

        if (shouldFire) {
            log.info("Alert fired: rule='{}' topic='{}' count={} threshold={}",
                    rule.getName(), rule.getDlqTopic().getDlqTopicName(), currentCount, rule.getThreshold());

            AlertEvent event = new AlertEvent();
            event.setAlertRule(rule);
            event.setStatus(AlertStatus.FIRING);
            event.setMessageCount(currentCount);
            event.setTriggeredAt(LocalDateTime.now());
            alertEventRepository.save(event);

            rule.setLastFiredAt(LocalDateTime.now());

            if (rule.getNotificationChannel() != null) {
                notificationService.sendNotification(rule.getNotificationChannel(), rule, currentCount);
            }
        }

        alertRuleRepository.save(rule);
    }
}
