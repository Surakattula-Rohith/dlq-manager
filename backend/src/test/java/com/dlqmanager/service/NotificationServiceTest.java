package com.dlqmanager.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest {

    private static final String REAL_LOOKING_WEBHOOK = "https://hooks.slack.com/services/T0TEST/B0TEST/secretTokenAbcd";

    @Test
    void acceptsSlackWebhook() {
        assertThatCode(() -> NotificationService.validateSlackWebhookUrl(REAL_LOOKING_WEBHOOK))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingWebhook() {
        assertThatThrownBy(() -> NotificationService.validateSlackWebhookUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationService.validateSlackWebhookUrl("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonSlackAddresses() {
        // Internal addresses must never be reachable through the "Test" button
        assertThatThrownBy(() -> NotificationService.validateSlackWebhookUrl("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationService.validateSlackWebhookUrl("http://localhost:8080/api/replay/bulk"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLookalikeDomainsAndPlainHttp() {
        assertThatThrownBy(() -> NotificationService.validateSlackWebhookUrl("https://hooks.slack.com.evil.example/services/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationService.validateSlackWebhookUrl("http://hooks.slack.com/services/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masksWebhookKeepingOnlyLastFourCharacters() {
        String masked = NotificationService.maskWebhookUrl(REAL_LOOKING_WEBHOOK);

        assertThat(masked).isEqualTo("https://hooks.slack.com/****Abcd");
        assertThat(masked).doesNotContain("secretToken");
        assertThat(NotificationService.isMasked(masked)).isTrue();
    }

    @Test
    void masksShortOrMissingValuesCompletely() {
        assertThat(NotificationService.maskWebhookUrl(null)).isEqualTo("****");
        assertThat(NotificationService.maskWebhookUrl("short")).isEqualTo("****");
    }

    @Test
    void realWebhookIsNotTreatedAsMasked() {
        assertThat(NotificationService.isMasked(REAL_LOOKING_WEBHOOK)).isFalse();
        assertThat(NotificationService.isMasked(null)).isFalse();
    }
}
