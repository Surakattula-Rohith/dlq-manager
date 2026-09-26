package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.NotificationChannelType;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.repository.NotificationChannelRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationChannelService {

    private static final String WEBHOOK_URL = "webhookUrl";

    private final NotificationChannelRepository notificationChannelRepository;
    private final NotificationService notificationService;
    private final AlertRuleRepository alertRuleRepository;
    private final ObjectMapper objectMapper;

    public List<NotificationChannel> getAll() {
        return notificationChannelRepository.findAll();
    }

    public Optional<NotificationChannel> getById(UUID id) {
        return notificationChannelRepository.findById(id);
    }

    public NotificationChannel create(String name, NotificationChannelType type, String configuration) {
        validateConfiguration(parseConfig(configuration));

        NotificationChannel channel = new NotificationChannel();
        channel.setName(name);
        channel.setType(type);
        channel.setConfiguration(configuration);
        channel.setEnabled(true);
        return notificationChannelRepository.save(channel);
    }

    public NotificationChannel update(UUID id, String name, NotificationChannelType type,
                                       String configuration, boolean enabled) {
        NotificationChannel channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification channel not found: " + id));

        // The UI only ever sees a masked webhook URL. If it sends the masked value back,
        // the user didn't change it - keep the real one we already have.
        Map<String, String> newConfig = parseConfig(configuration);
        if (NotificationService.isMasked(newConfig.get(WEBHOOK_URL))) {
            Map<String, String> existingConfig = parseConfig(channel.getConfiguration());
            newConfig.put(WEBHOOK_URL, existingConfig.get(WEBHOOK_URL));
        }
        validateConfiguration(newConfig);

        channel.setName(name);
        channel.setType(type);
        channel.setConfiguration(toJson(newConfig));
        channel.setEnabled(enabled);
        return notificationChannelRepository.save(channel);
    }

    @Transactional
    public void delete(UUID id) {
        // Null out the FK on any alert rules referencing this channel before deleting
        List<AlertRule> rules = alertRuleRepository.findByNotificationChannelId(id);
        for (AlertRule rule : rules) {
            rule.setNotificationChannel(null);
        }
        alertRuleRepository.saveAll(rules);
        notificationChannelRepository.deleteById(id);
    }

    public Map<String, Object> testChannel(UUID id) {
        NotificationChannel channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification channel not found: " + id));
        return notificationService.testChannel(channel);
    }

    /**
     * Channel configuration as it should be shown in API responses (secrets masked)
     */
    public String maskedConfiguration(NotificationChannel channel) {
        try {
            Map<String, String> config = parseConfig(channel.getConfiguration());
            if (config.containsKey(WEBHOOK_URL)) {
                config.put(WEBHOOK_URL, NotificationService.maskWebhookUrl(config.get(WEBHOOK_URL)));
            }
            return toJson(config);
        } catch (Exception e) {
            log.warn("Could not read configuration of channel {}", channel.getId());
            return "{}";
        }
    }

    // --- Helpers ---

    private void validateConfiguration(Map<String, String> config) {
        NotificationService.validateSlackWebhookUrl(config.get(WEBHOOK_URL));
    }

    private Map<String, String> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(json, new TypeReference<Map<String, String>>() {}));
        } catch (Exception e) {
            throw new IllegalArgumentException("configuration must be a JSON object, e.g. {\"webhookUrl\": \"https://hooks.slack.com/...\"}");
        }
    }

    private String toJson(Map<String, String> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not save configuration", e);
        }
    }
}
