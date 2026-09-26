package com.dlqmanager.service;

import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.AlertType;
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

    private static final String SLACK_WEBHOOK_PREFIX = "https://hooks.slack.com/";
    private static final String MASK = "****";

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

    private void sendSlack(NotificationChannel channel, AlertRule rule, long value) throws Exception {
        String webhookUrl = getSlackWebhookUrl(channel);

        // Threshold rules report pending messages, time-window rules report new arrivals
        String measurement = rule.getAlertType() == AlertType.TIME_WINDOW
                ? String.format("New messages in last %d min: *%d*", rule.getWindowMinutes(), value)
                : String.format("Pending messages: *%d*", value);

        String text = String.format(
                ":rotating_light: *DLQ Alert: %s*\nTopic: `%s` | %s | Threshold: %d",
                rule.getName(),
                rule.getDlqTopic().getDlqTopicName(),
                measurement,
                rule.getThreshold()
        );
        Map<String, Object> body = Map.of("text", text);
        restTemplate.postForObject(webhookUrl, body, String.class);
        log.info("Slack notification sent for rule '{}'", rule.getName());
    }

    private void testSlack(NotificationChannel channel) throws Exception {
        String webhookUrl = getSlackWebhookUrl(channel);
        Map<String, Object> body = Map.of("text", ":white_check_mark: DLQ Manager: Test notification from channel *" + channel.getName() + "*");
        restTemplate.postForObject(webhookUrl, body, String.class);
    }

    private String getSlackWebhookUrl(NotificationChannel channel) throws Exception {
        Map<String, String> config = parseConfig(channel.getConfiguration());
        String webhookUrl = config.get("webhookUrl");
        validateSlackWebhookUrl(webhookUrl);
        return webhookUrl;
    }

    // --- Webhook safety ---

    /**
     * Only real Slack webhooks are allowed.
     * Without this check, anyone could make the server send requests to any URL
     * (including internal services) by saving a channel and pressing "Test".
     */
    public static void validateSlackWebhookUrl(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("webhookUrl is missing");
        }
        if (!webhookUrl.startsWith(SLACK_WEBHOOK_PREFIX)) {
            throw new IllegalArgumentException("webhookUrl must start with " + SLACK_WEBHOOK_PREFIX);
        }
    }

    /**
     * Webhook URLs are secrets (anyone with the URL can post to the channel),
     * so API responses only show the last few characters.
     */
    public static String maskWebhookUrl(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.length() <= 8) {
            return MASK;
        }
        return SLACK_WEBHOOK_PREFIX + MASK + webhookUrl.substring(webhookUrl.length() - 4);
    }

    public static boolean isMasked(String value) {
        return value != null && value.contains(MASK);
    }

    // --- Helpers ---

    private Map<String, String> parseConfig(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }
}
