package com.dlqmanager.controller;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.service.AlertRuleService;
import com.dlqmanager.util.ApiTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alert-events")
@RequiredArgsConstructor
@Slf4j
public class AlertEventController {

    private final AlertRuleService alertRuleService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        List<AlertEvent> events = alertRuleService.getAllEvents();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("alertEvents", events.stream().map(this::toMap).toList());
        response.put("firingCount", alertRuleService.countFiringAlerts());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledge(@PathVariable UUID id, Authentication authentication) {
        try {
            AlertEvent event = alertRuleService.acknowledgeEvent(id, authentication.getName());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("alertEvent", toMap(event));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            r.put("error", e.getMessage());
            return ResponseEntity.status(500).body(r);
        }
    }

    @PostMapping("/{id}/snooze")
    public ResponseEntity<Map<String, Object>> snooze(@PathVariable UUID id,
                                                       @RequestBody Map<String, Object> body,
                                                       Authentication authentication) {
        try {
            int minutes = body.get("minutes") != null
                    ? Integer.parseInt(body.get("minutes").toString()) : 60;
            AlertEvent event = alertRuleService.snoozeEvent(id, minutes, authentication.getName());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("alertEvent", toMap(event));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            r.put("error", e.getMessage());
            return ResponseEntity.status(500).body(r);
        }
    }

    private Map<String, Object> toMap(AlertEvent event) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", event.getId().toString());
        m.put("alertRuleId", event.getAlertRule().getId().toString());
        m.put("alertRuleName", event.getAlertRule().getName());
        m.put("dlqTopicName", event.getAlertRule().getDlqTopic().getDlqTopicName());
        m.put("status", event.getStatus().name());
        m.put("messageCount", event.getMessageCount());
        m.put("triggeredAt", ApiTime.utc(event.getTriggeredAt()));
        m.put("acknowledgedAt", ApiTime.utc(event.getAcknowledgedAt()));
        m.put("acknowledgedBy", event.getAcknowledgedBy());
        m.put("snoozedUntil", ApiTime.utc(event.getSnoozedUntil()));
        m.put("resolvedAt", ApiTime.utc(event.getResolvedAt()));
        m.put("snoozedBy", event.getSnoozedBy());
        return m;
    }
}
