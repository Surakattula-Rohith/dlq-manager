package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.ActivityAction;
import com.dlqmanager.model.enums.AlertStatus;
import com.dlqmanager.model.enums.AlertType;
import com.dlqmanager.repository.AlertEventRepository;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.repository.DlqTopicRepository;
import com.dlqmanager.repository.NotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final DlqTopicRepository dlqTopicRepository;
    private final NotificationChannelRepository notificationChannelRepository;
    private final ActivityLogService activityLogService;

    public List<AlertRule> getAll() {
        return alertRuleRepository.findAll();
    }

    public Optional<AlertRule> getById(UUID id) {
        return alertRuleRepository.findById(id);
    }

    public AlertRule create(String name, UUID dlqTopicId, AlertType alertType,
                             Long threshold, Integer windowMinutes,
                             UUID notificationChannelId, Integer cooldownMinutes) {
        DlqTopic topic = dlqTopicRepository.findById(dlqTopicId)
                .orElseThrow(() -> new RuntimeException("DLQ topic not found: " + dlqTopicId));

        AlertRule rule = new AlertRule();
        rule.setName(name);
        rule.setDlqTopic(topic);
        rule.setAlertType(alertType);
        rule.setThreshold(threshold);
        rule.setWindowMinutes(windowMinutes);
        rule.setCooldownMinutes(cooldownMinutes != null ? cooldownMinutes : 30);
        rule.setEnabled(true);

        if (notificationChannelId != null) {
            NotificationChannel channel = notificationChannelRepository.findById(notificationChannelId)
                    .orElseThrow(() -> new RuntimeException("Notification channel not found: " + notificationChannelId));
            rule.setNotificationChannel(channel);
        }

        AlertRule saved = alertRuleRepository.save(rule);
        activityLogService.record(ActivityAction.ALERT_RULE_CREATED, saved.getName(), describe(saved));
        return saved;
    }

    @Transactional
    public AlertRule update(UUID id, String name, AlertType alertType,
                             Long threshold, Integer windowMinutes,
                             UUID notificationChannelId, Integer cooldownMinutes, boolean enabled) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert rule not found: " + id));

        rule.setName(name);
        rule.setAlertType(alertType);
        rule.setThreshold(threshold);
        rule.setWindowMinutes(windowMinutes);
        rule.setCooldownMinutes(cooldownMinutes != null ? cooldownMinutes : 30);
        rule.setEnabled(enabled);

        if (notificationChannelId != null) {
            NotificationChannel channel = notificationChannelRepository.findById(notificationChannelId)
                    .orElseThrow(() -> new RuntimeException("Notification channel not found: " + notificationChannelId));
            rule.setNotificationChannel(channel);
        } else {
            rule.setNotificationChannel(null);
        }

        AlertRule saved = alertRuleRepository.save(rule);
        activityLogService.record(ActivityAction.ALERT_RULE_UPDATED, saved.getName(), describe(saved));
        return saved;
    }

    public AlertRule toggleEnabled(UUID id) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert rule not found: " + id));
        rule.setEnabled(!rule.isEnabled());
        AlertRule saved = alertRuleRepository.save(rule);
        activityLogService.record(saved.isEnabled() ? ActivityAction.ALERT_RULE_ENABLED : ActivityAction.ALERT_RULE_DISABLED,
                saved.getName(), null);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        Optional<AlertRule> rule = alertRuleRepository.findById(id);
        alertEventRepository.deleteByAlertRuleId(id);
        alertRuleRepository.deleteById(id);
        rule.ifPresent(deleted -> activityLogService.record(ActivityAction.ALERT_RULE_DELETED, deleted.getName(), null));
    }

    @Transactional
    public AlertEvent acknowledgeEvent(UUID eventId, String username) {
        AlertEvent event = alertEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Alert event not found: " + eventId));
        event.setStatus(AlertStatus.ACKNOWLEDGED);
        event.setAcknowledgedAt(LocalDateTime.now());
        event.setAcknowledgedBy(username);
        AlertEvent saved = alertEventRepository.save(event);
        activityLogService.record(username, ActivityAction.ALERT_ACKNOWLEDGED,
                event.getAlertRule().getName(), event.getAlertRule().getDlqTopic().getDlqTopicName());
        return saved;
    }

    @Transactional
    public AlertEvent snoozeEvent(UUID eventId, int snoozeMinutes, String username) {
        AlertEvent event = alertEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Alert event not found: " + eventId));
        event.setStatus(AlertStatus.SNOOZED);
        event.setSnoozedUntil(LocalDateTime.now().plusMinutes(snoozeMinutes));
        event.setSnoozedBy(username);
        AlertEvent saved = alertEventRepository.save(event);
        activityLogService.record(username, ActivityAction.ALERT_SNOOZED, event.getAlertRule().getName(),
                event.getAlertRule().getDlqTopic().getDlqTopicName() + ", for " + snoozeMinutes + " min");
        return saved;
    }

    /**
     * Short description of a rule for the activity log, e.g. "orders-dlq: 50 or more pending messages"
     */
    private static String describe(AlertRule rule) {
        String condition = rule.getAlertType() == AlertType.TIME_WINDOW
                ? rule.getThreshold() + " new messages in " + rule.getWindowMinutes() + " min"
                : rule.getThreshold() + " or more pending messages";
        return rule.getDlqTopic().getDlqTopicName() + ": " + condition;
    }

    public List<AlertEvent> getAllEvents() {
        return alertEventRepository.findAllByOrderByTriggeredAtDesc();
    }

    public long countFiringAlerts() {
        // Firing and not resolved: the problem is still there and nobody has acknowledged or snoozed it
        return alertEventRepository.countByStatusAndResolvedAtIsNull(AlertStatus.FIRING);
    }
}
