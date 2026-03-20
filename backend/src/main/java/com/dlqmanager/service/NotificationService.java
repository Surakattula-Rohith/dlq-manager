package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.NotificationChannel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public void sendNotification(NotificationChannel channel, AlertRule rule, long messageCount) {
        if (!channel.isEnabled()) return;
        try {
            sendSlack(channel, rule, messageCount);
        } catch (Exception e) {
            log.error("Failed to send Slack notification for rule '{}': {}",
                    rule.getName(), e.getMessage());
        }
    }

    public Map<String, Object> testChannel(NotificationChannel channel) {
        Map<String, Object> result = new HashMap<>();
        try {
            testSlack(channel);
            result.put("success", true);
            result.put("message", "Test notification sent successfully");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // --- Slack ---

    private void sendSlack(NotificationChannel channel, AlertRule rule, long messageCount) throws Exception {
        Map<String, String> config = parseConfig(channel.getConfiguration());
        String webhookUrl = config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) throw new IllegalArgumentException("webhookUrl is missing");

        String text = String.format(
                ":rotating_light: *DLQ Alert: %s*\nTopic: `%s` | Messages: *%d* | Threshold: %d",
                rule.getName(),
                rule.getDlqTopic().getDlqTopicName(),
                messageCount,
                rule.getThreshold()
        );
        Map<String, Object> body = Map.of("text", text);
        restTemplate.postForObject(webhookUrl, body, String.class);
        log.info("Slack notification sent for rule '{}'", rule.getName());
    }

    private void testSlack(NotificationChannel channel) throws Exception {
        Map<String, String> config = parseConfig(channel.getConfiguration());
        String webhookUrl = config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) throw new IllegalArgumentException("webhookUrl is missing");
        Map<String, Object> body = Map.of("text", ":white_check_mark: DLQ Manager: Test notification from channel *" + channel.getName() + "*");
        restTemplate.postForObject(webhookUrl, body, String.class);
    }

    // --- Helpers ---

    private Map<String, String> parseConfig(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }
}
