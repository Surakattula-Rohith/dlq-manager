package com.dlqmanager.service;

import com.dlqmanager.model.entity.ActivityEvent;
import com.dlqmanager.model.enums.ActivityAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackActivityMessageTest {

    @Test
    void replayReadsAsOneLine() {
        String text = SlackActivityMessage.format(event("operator", ActivityAction.MESSAGES_REPLAYED,
                "orders-dlq", "12 message(s): 12 succeeded, 0 failed"));

        assertThat(text).isEqualTo(
                ":arrows_counterclockwise: *operator* replayed messages from `orders-dlq` - 12 message(s): 12 succeeded, 0 failed");
    }

    @Test
    void alertActionsNameTheRuleAndTopic() {
        assertThat(SlackActivityMessage.format(event("operator", ActivityAction.ALERT_SNOOZED,
                "Payments backlog", "payments-dlq, for 60 min")))
                .isEqualTo(":zzz: *operator* snoozed the alert `Payments backlog` - payments-dlq, for 60 min");
        assertThat(SlackActivityMessage.format(event("operator", ActivityAction.ALERT_ACKNOWLEDGED,
                "Orders backlog", "orders-dlq")))
                .isEqualTo(":eyes: *operator* acknowledged the alert `Orders backlog` - orders-dlq");
    }

    @Test
    void setupChangesSayWhatChanged() {
        assertThat(SlackActivityMessage.format(event("admin", ActivityAction.KAFKA_SETTINGS_CHANGED,
                "Kafka connection", "kafka:29092 (no login, not encrypted) -> broker1:9093 (PLAIN login as app, encrypted)")))
                .isEqualTo(":gear: *admin* changed the `Kafka connection` - "
                        + "kafka:29092 (no login, not encrypted) -&gt; broker1:9093 (PLAIN login as app, encrypted)");
    }

    @Test
    void entryWithoutDetailsEndsAfterTheTarget() {
        assertThat(SlackActivityMessage.format(event("admin", ActivityAction.DLQ_TOPIC_DELETED, "old-dlq", null)))
                .isEqualTo(":gear: *admin* deleted DLQ topic `old-dlq`");
    }

    @Test
    void namesTypedByPeopleCannotPingTheChannelOrAddLinks() {
        String text = SlackActivityMessage.format(event("admin", ActivityAction.ALERT_RULE_CREATED,
                "<!channel> urgent", "see <https://evil.example|here> & there"));

        assertThat(text).doesNotContain("<").doesNotContain(">");
        assertThat(text).contains("&lt;!channel&gt; urgent").contains("&lt;https://evil.example|here&gt; &amp; there");
    }

    @Test
    void backtickInANameDoesNotBreakTheFormatting() {
        String text = SlackActivityMessage.format(event("admin", ActivityAction.CHANNEL_CREATED, "#ops`alerts", "SLACK"));

        assertThat(text).isEqualTo(":gear: *admin* added Slack channel `#ops'alerts` - SLACK");
    }

    @Test
    void everyActionHasWording() {
        for (ActivityAction action : ActivityAction.values()) {
            assertThat(SlackActivityMessage.format(event("someone", action, "target", null)))
                    .as(action.name())
                    .contains("*someone* ")
                    .contains("`target`");
        }
    }

    static ActivityEvent event(String username, ActivityAction action, String target, String details) {
        ActivityEvent event = new ActivityEvent();
        event.setUsername(username);
        event.setAction(action);
        event.setTarget(target);
        event.setDetails(details);
        return event;
    }
}
