package com.dlqmanager.service;

import com.dlqmanager.model.entity.ActivityEvent;
import com.dlqmanager.model.enums.ActivityAction;
import com.dlqmanager.model.enums.ActivityCategory;

/**
 * Turns an activity log entry into one line of Slack text
 *
 * Examples:
 *   :arrows_counterclockwise: *operator* replayed messages from `orders-dlq` - 12 message(s): 12 succeeded, 0 failed
 *   :zzz: *operator* snoozed the alert `Payments backlog` - payments-dlq, for 60 min
 *   :gear: *admin* added DLQ topic `payments-dlq` - source: payments, status: ACTIVE
 */
final class SlackActivityMessage {

    private SlackActivityMessage() {
    }

    static String format(ActivityEvent event) {
        StringBuilder text = new StringBuilder()
                .append(emoji(event.getAction())).append(" *").append(escape(event.getUsername())).append("* ")
                .append(phrase(event.getAction()));

        if (event.getTarget() != null && !event.getTarget().isBlank()) {
            // Inside `code` a backtick would end the formatting early
            text.append(" `").append(escape(event.getTarget()).replace('`', '\'')).append('`');
        }
        if (event.getDetails() != null && !event.getDetails().isBlank()) {
            text.append(" - ").append(escape(event.getDetails()));
        }
        return text.toString();
    }

    /**
     * What the person did, written to be followed by the target (a topic, rule or channel name)
     */
    private static String phrase(ActivityAction action) {
        return switch (action) {
            case MESSAGES_REPLAYED -> "replayed messages from";
            case ALERT_ACKNOWLEDGED -> "acknowledged the alert";
            case ALERT_SNOOZED -> "snoozed the alert";
            case DLQ_TOPIC_ADDED -> "added DLQ topic";
            case DLQ_TOPIC_UPDATED -> "updated DLQ topic";
            case DLQ_TOPIC_DELETED -> "deleted DLQ topic";
            case ALERT_RULE_CREATED -> "created alert rule";
            case ALERT_RULE_UPDATED -> "updated alert rule";
            case ALERT_RULE_ENABLED -> "enabled alert rule";
            case ALERT_RULE_DISABLED -> "disabled alert rule";
            case ALERT_RULE_DELETED -> "deleted alert rule";
            case CHANNEL_CREATED -> "added Slack channel";
            case CHANNEL_UPDATED -> "updated Slack channel";
            case CHANNEL_DELETED -> "deleted Slack channel";
            case KAFKA_SETTINGS_CHANGED -> "changed the";
            case SIGNED_IN -> "signed in";
            case SIGNED_OUT -> "signed out";
            case SIGN_IN_FAILED -> "failed to sign in";
        };
    }

    private static String emoji(ActivityAction action) {
        if (action == ActivityAction.ALERT_SNOOZED) {
            return ":zzz:";
        }
        ActivityCategory category = action.category();
        if (category == ActivityCategory.REPLAYS) {
            return ":arrows_counterclockwise:";
        }
        if (category == ActivityCategory.ALERTS) {
            return ":eyes:";
        }
        return ":gear:";
    }

    /**
     * Slack reads &lt;...&gt; as links and mentions. Names typed by people (a rule called
     * "&lt;!channel&gt;", say) must show up as text and not ping the whole channel.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
