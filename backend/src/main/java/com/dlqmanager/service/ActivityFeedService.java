package com.dlqmanager.service;

import com.dlqmanager.model.entity.ActivityEvent;
import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.ActivityCategory;
import com.dlqmanager.repository.NotificationChannelRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Team feed: posts what people do to the Slack channels that follow it
 *
 * A team working an incident wants to see "operator replayed 12 messages from orders-dlq"
 * in their channel without opening the app. Each Slack channel chooses which kinds of
 * activity it follows (see ActivityCategory).
 *
 * Slack must never slow down or break the action being reported, so posting happens
 * on one background thread:
 * - the action returns as soon as the post is queued
 * - posts go out in the order things happened
 * - if Slack is slow and the queue fills up, new posts are dropped (and logged);
 *   the Activity page still has everything
 */
@Service
@Slf4j
public class ActivityFeedService {

    private static final int MAX_WAITING_POSTS = 200;

    private final NotificationChannelRepository notificationChannelRepository;
    private final NotificationService notificationService;
    private final Executor executor;

    @Autowired
    public ActivityFeedService(NotificationChannelRepository notificationChannelRepository,
                               NotificationService notificationService) {
        this(notificationChannelRepository, notificationService, newBackgroundExecutor());
    }

    ActivityFeedService(NotificationChannelRepository notificationChannelRepository,
                        NotificationService notificationService,
                        Executor executor) {
        this.notificationChannelRepository = notificationChannelRepository;
        this.notificationService = notificationService;
        this.executor = executor;
    }

    /**
     * Queue an activity entry for the channels that follow its category. Never throws.
     */
    public void post(ActivityEvent event) {
        ActivityCategory category = event.getAction().category();
        if (category == null) {
            return;
        }

        // Built now, on the caller's thread: the entry is not touched again in the background
        String text = SlackActivityMessage.format(event);
        try {
            executor.execute(() -> deliver(category, text));
        } catch (RejectedExecutionException e) {
            log.warn("Team feed is backed up, not posting: {}", text);
        }
    }

    private void deliver(ActivityCategory category, String text) {
        try {
            for (NotificationChannel channel : notificationChannelRepository.findByEnabledTrue()) {
                if (channel.getActivityFeed().contains(category)) {
                    send(channel, text);
                }
            }
        } catch (Exception e) {
            log.error("Could not post to the team feed: {}", e.getMessage());
        }
    }

    /**
     * One channel failing (deleted webhook, Slack down) must not stop the others
     */
    private void send(NotificationChannel channel, String text) {
        try {
            notificationService.sendText(channel, text);
        } catch (Exception e) {
            log.error("Could not post to Slack channel '{}': {}", channel.getName(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }

    private static ExecutorService newBackgroundExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_WAITING_POSTS),
                task -> {
                    Thread thread = new Thread(task, "team-feed");
                    thread.setDaemon(true);
                    return thread;
                });
    }
}
