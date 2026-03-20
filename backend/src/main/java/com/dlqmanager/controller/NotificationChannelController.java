package com.dlqmanager.controller;

import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.NotificationChannelType;
import com.dlqmanager.service.NotificationChannelService;
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
@RequestMapping("/api/notification-channels")
@RequiredArgsConstructor
@Slf4j
public class NotificationChannelController {

    private final NotificationChannelService notificationChannelService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        List<NotificationChannel> channels = notificationChannelService.getAll();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("channels", channels.stream().map(this::toMap).toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID id) {
        return notificationChannelService.getById(id)
                .map(channel -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("channel", toMap(channel));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            NotificationChannelType type = NotificationChannelType.valueOf((String) body.get("type"));
            String configuration = (String) body.get("configuration");

            if (name == null || name.isBlank()) {
                return badRequest("name is required");
            }
            if (configuration == null || configuration.isBlank()) {
                return badRequest("configuration is required");
            }

            NotificationChannel channel = notificationChannelService.create(name, type, configuration);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("channel", toMap(channel));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id,
                                                       @RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            NotificationChannelType type = NotificationChannelType.valueOf((String) body.get("type"));
            String configuration = (String) body.get("configuration");
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"));

            NotificationChannel channel = notificationChannelService.update(id, name, type, configuration, enabled);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("channel", toMap(channel));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        try {
            notificationChannelService.delete(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationChannelService.testChannel(id));
    }

    private Map<String, Object> toMap(NotificationChannel channel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", channel.getId().toString());
        m.put("name", channel.getName());
        m.put("type", channel.getType().name());
        m.put("configuration", channel.getConfiguration());
        m.put("enabled", channel.isEnabled());
        m.put("createdAt", channel.getCreatedAt() != null ? channel.getCreatedAt().toString() : null);
        m.put("updatedAt", channel.getUpdatedAt() != null ? channel.getUpdatedAt().toString() : null);
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
