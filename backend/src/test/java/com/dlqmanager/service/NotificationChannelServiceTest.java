package com.dlqmanager.service;

import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.NotificationChannelType;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.repository.NotificationChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationChannelServiceTest {

    private static final String REAL_WEBHOOK = "https://hooks.slack.com/services/T0TEST/B0TEST/realSecret1234";

    @Mock
    private NotificationChannelRepository notificationChannelRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private ActivityLogService activityLogService;

    private NotificationChannelService service;

    @BeforeEach
    void setUp() {
        service = new NotificationChannelService(
                notificationChannelRepository, notificationService, alertRuleRepository, new ObjectMapper(),
                activityLogService);
    }

    @Test
    void createRejectsNonSlackWebhook() {
        assertThatThrownBy(() -> service.create("evil", NotificationChannelType.SLACK,
                "{\"webhookUrl\":\"http://169.254.169.254/latest/meta-data\"}"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(notificationChannelRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidJson() {
        assertThatThrownBy(() -> service.create("broken", NotificationChannelType.SLACK, "not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateWithMaskedValueKeepsStoredWebhook() {
        NotificationChannel existing = channel(REAL_WEBHOOK);
        when(notificationChannelRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(notificationChannelRepository.save(any())).then(returnsFirstArg());

        // The UI only ever saw the masked value and sends it back unchanged
        NotificationChannel updated = service.update(existing.getId(), "#renamed", NotificationChannelType.SLACK,
                "{\"webhookUrl\":\"https://hooks.slack.com/****1234\"}", true);

        assertThat(updated.getName()).isEqualTo("#renamed");
        assertThat(updated.getConfiguration()).contains(REAL_WEBHOOK);
    }

    @Test
    void updateWithNewWebhookReplacesIt() {
        NotificationChannel existing = channel(REAL_WEBHOOK);
        String newWebhook = "https://hooks.slack.com/services/T0TEST/B0TEST/newSecret5678";
        when(notificationChannelRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(notificationChannelRepository.save(any())).then(returnsFirstArg());

        NotificationChannel updated = service.update(existing.getId(), "#alerts", NotificationChannelType.SLACK,
                "{\"webhookUrl\":\"" + newWebhook + "\"}", true);

        assertThat(updated.getConfiguration()).contains(newWebhook).doesNotContain(REAL_WEBHOOK);
    }

    @Test
    void maskedConfigurationHidesSecret() {
        String masked = service.maskedConfiguration(channel(REAL_WEBHOOK));

        assertThat(masked).isEqualTo("{\"webhookUrl\":\"https://hooks.slack.com/****1234\"}");
        assertThat(masked).doesNotContain("realSecret");
    }

    private NotificationChannel channel(String webhookUrl) {
        NotificationChannel channel = new NotificationChannel();
        channel.setId(UUID.randomUUID());
        channel.setName("#alerts");
        channel.setType(NotificationChannelType.SLACK);
        channel.setConfiguration("{\"webhookUrl\":\"" + webhookUrl + "\"}");
        channel.setEnabled(true);
        return channel;
    }
}
