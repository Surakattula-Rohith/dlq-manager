package com.dlqmanager.controller;

import com.dlqmanager.model.entity.ActivityEvent;
import com.dlqmanager.model.enums.ActivityAction;
import com.dlqmanager.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Activity log: who did what and when
 */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityLogService activityLogService;

    /**
     * GET /api/activity?page=1&size=25&username=operator&action=MESSAGES_REPLAYED
     *
     * Newest first. username and action are optional filters.
     */
    @GetMapping
    public Map<String, Object> getActivity(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) ActivityAction action
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Page<ActivityEvent> result = activityLogService.search(username, action, safePage, safeSize);

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("currentPage", safePage);
        pagination.put("pageSize", safeSize);
        pagination.put("totalItems", result.getTotalElements());
        pagination.put("totalPages", Math.max(1, result.getTotalPages()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("activity", result.getContent().stream().map(this::toMap).toList());
        response.put("pagination", pagination);
        response.put("usernames", activityLogService.usernames());
        return response;
    }

    private Map<String, Object> toMap(ActivityEvent event) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", event.getId().toString());
        // The app runs in UTC, so send an instant the browser can show in local time
        m.put("occurredAt", event.getOccurredAt().toInstant(ZoneOffset.UTC).toString());
        m.put("username", event.getUsername());
        m.put("action", event.getAction().name());
        m.put("target", event.getTarget());
        m.put("details", event.getDetails());
        return m;
    }
}
