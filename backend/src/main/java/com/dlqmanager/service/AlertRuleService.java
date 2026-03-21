package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.entity.NotificationChannel;
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

        return alertRuleRepository.save(rule);
    }

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

        return alertRuleRepository.save(rule);
    }

    public AlertRule toggleEnabled(UUID id) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert rule not found: " + id));
        rule.setEnabled(!rule.isEnabled());
        return alertRuleRepository.save(rule);
    }

    @Transactional
    public void delete(UUID id) {
        alertEventRepository.deleteByAlertRuleId(id);
        alertRuleRepository.deleteById(id);
    }

    public AlertEvent acknowledgeEvent(UUID eventId) {
        AlertEvent event = alertEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Alert event not found: " + eventId));
        event.setStatus(AlertStatus.ACKNOWLEDGED);
        event.setAcknowledgedAt(LocalDateTime.now());
        return alertEventRepository.save(event);
    }

    public AlertEvent snoozeEvent(UUID eventId, int snoozeMinutes) {
        AlertEvent event = alertEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Alert event not found: " + eventId));
        event.setStatus(AlertStatus.SNOOZED);
        event.setSnoozedUntil(LocalDateTime.now().plusMinutes(snoozeMinutes));
        return alertEventRepository.save(event);
    }

    public List<AlertEvent> getAllEvents() {
        return alertEventRepository.findAllByOrderByTriggeredAtDesc();
    }

    public long countFiringAlerts() {
        return alertEventRepository.countByStatus(AlertStatus.FIRING);
    }
}
