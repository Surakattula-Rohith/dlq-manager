package com.dlqmanager.controller;

import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.enums.AlertType;
import com.dlqmanager.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
@Slf4j
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        List<AlertRule> rules = alertRuleService.getAll();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("alertRules", rules.stream().map(this::toMap).toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID id) {
        return alertRuleService.getById(id)
                .map(rule -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("alertRule", toMap(rule));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            UUID dlqTopicId = UUID.fromString((String) body.get("dlqTopicId"));
            AlertType alertType = AlertType.valueOf((String) body.get("alertType"));
            Long threshold = Long.valueOf(body.get("threshold").toString());
            Integer windowMinutes = body.get("windowMinutes") != null
                    ? Integer.valueOf(body.get("windowMinutes").toString()) : null;
            UUID notificationChannelId = body.get("notificationChannelId") != null
                    ? UUID.fromString((String) body.get("notificationChannelId")) : null;
            Integer cooldownMinutes = body.get("cooldownMinutes") != null
                    ? Integer.valueOf(body.get("cooldownMinutes").toString()) : 30;

            if (name == null || name.isBlank()) return badRequest("name is required");

            AlertRule rule = alertRuleService.create(name, dlqTopicId, alertType, threshold,
                    windowMinutes, notificationChannelId, cooldownMinutes);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("alertRule", toMap(rule));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create alert rule", e);
            return errorResponse(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id,
                                                       @RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            AlertType alertType = AlertType.valueOf((String) body.get("alertType"));
            Long threshold = Long.valueOf(body.get("threshold").toString());
            Integer windowMinutes = body.get("windowMinutes") != null
                    ? Integer.valueOf(body.get("windowMinutes").toString()) : null;
            UUID notificationChannelId = body.get("notificationChannelId") != null
                    ? UUID.fromString((String) body.get("notificationChannelId")) : null;
            Integer cooldownMinutes = body.get("cooldownMinutes") != null
                    ? Integer.valueOf(body.get("cooldownMinutes").toString()) : 30;
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"));

            AlertRule rule = alertRuleService.update(id, name, alertType, threshold,
                    windowMinutes, notificationChannelId, cooldownMinutes, enabled);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("alertRule", toMap(rule));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable UUID id) {
        try {
            AlertRule rule = alertRuleService.toggleEnabled(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("alertRule", toMap(rule));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        try {
            alertRuleService.delete(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    private Map<String, Object> toMap(AlertRule rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rule.getId().toString());
        m.put("name", rule.getName());
        m.put("dlqTopicId", rule.getDlqTopic().getId().toString());
        m.put("dlqTopicName", rule.getDlqTopic().getDlqTopicName());
        m.put("alertType", rule.getAlertType().name());
        m.put("threshold", rule.getThreshold());
        m.put("windowMinutes", rule.getWindowMinutes());
        m.put("cooldownMinutes", rule.getCooldownMinutes());
        m.put("enabled", rule.isEnabled());
        m.put("lastFiredAt", rule.getLastFiredAt() != null ? rule.getLastFiredAt().toString() : null);
        m.put("createdAt", rule.getCreatedAt() != null ? rule.getCreatedAt().toString() : null);
        m.put("updatedAt", rule.getUpdatedAt() != null ? rule.getUpdatedAt().toString() : null);
        if (rule.getNotificationChannel() != null) {
            m.put("notificationChannelId", rule.getNotificationChannel().getId().toString());
            m.put("notificationChannelName", rule.getNotificationChannel().getName());
            m.put("notificationChannelType", rule.getNotificationChannel().getType().name());
        } else {
            m.put("notificationChannelId", null);
            m.put("notificationChannelName", null);
            m.put("notificationChannelType", null);
        }
        return m;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> r = new HashMap<>();
        r.put("success", false);
        r.put("error", message);
        return ResponseEntity.badRequest().body(r);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String message) {
        Map<String, Object> r = new HashMap<>();
        r.put("success", false);
        r.put("error", message);
        return ResponseEntity.status(500).body(r);
    }
}
