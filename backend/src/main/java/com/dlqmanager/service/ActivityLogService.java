package com.dlqmanager.service;

import com.dlqmanager.model.entity.ActivityEvent;
import com.dlqmanager.model.enums.ActivityAction;
import com.dlqmanager.repository.ActivityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Activity Log
 *
 * Records who did what. Services call record(...) right after a change succeeds,
 * so every way of making that change (UI, script, future code) is logged the same way.
 *
 * Writing the log must never break the action itself: if saving an entry fails,
 * the error is logged and the action carries on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    static final String SYSTEM_USER = "system";
    private static final int MAX_USERNAME = 100;
    private static final int MAX_TARGET = 255;
    private static final int MAX_DETAILS = 1000;

    private final ActivityEventRepository activityEventRepository;

    /**
     * Record an action by the signed-in user ("system" when nobody is signed in, e.g. scheduled jobs)
     */
    public void record(ActivityAction action, String target, String details) {
        record(currentUsername(), action, target, details);
    }

    /**
     * Record an action by a known user
     */
    public void record(String username, ActivityAction action, String target, String details) {
        try {
            ActivityEvent event = new ActivityEvent();
            event.setOccurredAt(LocalDateTime.now());
            event.setUsername(truncate(username != null && !username.isBlank() ? username : SYSTEM_USER, MAX_USERNAME));
            event.setAction(action);
            event.setTarget(truncate(target, MAX_TARGET));
            event.setDetails(truncate(details, MAX_DETAILS));
            activityEventRepository.save(event);
        } catch (Exception e) {
            log.error("Could not record activity {} by {} on {}", action, username, target, e);
        }
    }

    /**
     * Newest first, optionally filtered by user and/or action
     *
     * @param page 1-based page number
     */
    public Page<ActivityEvent> search(String username, ActivityAction action, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        String user = username != null && !username.isBlank() ? username.trim() : null;
        return activityEventRepository.search(user, action, pageRequest);
    }

    public List<String> usernames() {
        return activityEventRepository.findUsernames();
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM_USER;
        }
        return authentication.getName();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
