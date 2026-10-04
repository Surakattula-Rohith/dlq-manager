package com.dlqmanager.service;

import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.ActivityAction;
import com.dlqmanager.model.enums.ActivityCategory;
import com.dlqmanager.repository.NotificationChannelRepository;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static com.dlqmanager.service.SlackActivityMessageTest.event;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActivityFeedServiceTest {

    private final NotificationChannelRepository channels = mock(NotificationChannelRepository.class);
    private final NotificationService slack = mock(NotificationService.class);
    // Runs the "background" work straight away, so the tests can check the result
    private final ActivityFeedService feed = new ActivityFeedService(channels, slack, Runnable::run);

    @Test
    void postsOnlyToChannelsThatFollowThatKindOfActivity() throws Exception {
        NotificationChannel incidents = channel("#incidents", ActivityCategory.REPLAYS, ActivityCategory.ALERTS);
        NotificationChannel platform = channel("#platform", ActivityCategory.CHANGES);
        NotificationChannel alertsOnly = channel("#alerts-only");
        when(channels.findByEnabledTrue()).thenReturn(List.of(incidents, platform, alertsOnly));

        feed.post(event("operator", ActivityAction.MESSAGES_REPLAYED, "orders-dlq", "2 message(s): 2 succeeded, 0 failed"));

        verify(slack).sendText(eq(incidents), contains("*operator* replayed messages from `orders-dlq`"));
        verify(slack, never()).sendText(eq(platform), anyString());
        verify(slack, never()).sendText(eq(alertsOnly), anyString());
    }

    @Test
    void signInsAreNeverPosted() {
        feed.post(event("viewer", ActivityAction.SIGNED_IN, null, null));
        feed.post(event("mallory", ActivityAction.SIGN_IN_FAILED, null, null));

        verifyNoInteractions(channels, slack);
    }

    @Test
    void oneBrokenChannelDoesNotStopTheOthers() throws Exception {
        NotificationChannel broken = channel("#deleted-webhook", ActivityCategory.CHANGES);
        NotificationChannel working = channel("#platform", ActivityCategory.CHANGES);
        when(channels.findByEnabledTrue()).thenReturn(List.of(broken, working));
        doThrow(new IllegalStateException("404 no_service")).when(slack).sendText(eq(broken), anyString());

        feed.post(event("admin", ActivityAction.DLQ_TOPIC_ADDED, "payments-dlq", "source: payments, status: ACTIVE"));

        verify(slack).sendText(eq(working), contains("added DLQ topic `payments-dlq`"));
    }

    @Test
    void slackOrDatabaseTroubleNeverReachesThePersonWhoActed() throws Exception {
        when(channels.findByEnabledTrue()).thenThrow(new IllegalStateException("database is down"));

        assertThatCode(() -> feed.post(event("operator", ActivityAction.ALERT_ACKNOWLEDGED, "Orders backlog", "orders-dlq")))
                .doesNotThrowAnyException();
    }

    @Test
    void whenTheQueueIsFullThePostIsDroppedInsteadOfBlocking() throws Exception {
        Executor full = task -> {
            throw new RejectedExecutionException("queue full");
        };
        ActivityFeedService backedUp = new ActivityFeedService(channels, slack, full);

        assertThatCode(() -> backedUp.post(event("operator", ActivityAction.MESSAGES_REPLAYED, "orders-dlq", null)))
                .doesNotThrowAnyException();
        verify(slack, never()).sendText(any(), anyString());
    }

    private static NotificationChannel channel(String name, ActivityCategory... follows) {
        NotificationChannel channel = new NotificationChannel();
        channel.setName(name);
        channel.setEnabled(true);
        Set<ActivityCategory> feed = EnumSet.noneOf(ActivityCategory.class);
        feed.addAll(List.of(follows));
        channel.setActivityFeed(feed);
        return channel;
    }
}
