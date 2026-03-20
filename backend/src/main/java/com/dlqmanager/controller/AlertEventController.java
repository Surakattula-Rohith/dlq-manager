package com.dlqmanager.controller;

import com.dlqmanager.model.entity.AlertEvent;
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
    public ResponseEntity<Map<String, Object>> acknowledge(@PathVariable UUID id) {
        try {
            AlertEvent event = alertRuleService.acknowledgeEvent(id);
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
                                                       @RequestBody Map<String, Object> body) {
        try {
            int minutes = body.get("minutes") != null
                    ? Integer.parseInt(body.get("minutes").toString()) : 60;
            AlertEvent event = alertRuleService.snoozeEvent(id, minutes);
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
        m.put("triggeredAt", event.getTriggeredAt() != null ? event.getTriggeredAt().toString() : null);
        m.put("acknowledgedAt", event.getAcknowledgedAt() != null ? event.getAcknowledgedAt().toString() : null);
        m.put("snoozedUntil", event.getSnoozedUntil() != null ? event.getSnoozedUntil().toString() : null);
        return m;
    }
}
