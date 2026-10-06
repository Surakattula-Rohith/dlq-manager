package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.dto.BulkReplayRequestDto;
import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.enums.AlertStatus;
import com.dlqmanager.repository.AlertEventRepository;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.service.ReplayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every time the API sends is a UTC instant ("...Z")
 *
 * A time without a zone is read by browsers as local time, which showed replay history
 * and alerts hours off for everyone outside UTC.
 */
@AutoConfigureMockMvc
class ApiTimesIntegrationTest extends IntegrationTestBase {

    /** A date-time in JSON, with whatever follows the seconds up to the closing quote */
    private static final Pattern TIME = Pattern.compile("\"(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\"]*)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReplayService replayService;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @Test
    void everyTimeInEveryListSaysItIsUtc() throws Exception {
        // One of everything that carries a time
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        createTopic(source, 1);
        createTopic(dlq, 1);
        produce(dlq, 0, 1, Map.of());
        UUID dlqTopicId = UUID.fromString(objectMapper.readTree(send("/api/dlq-topics", """
                {"dlqTopicName": "%s", "sourceTopic": "%s", "detectionType": "MANUAL"}
                """.formatted(dlq, source), 201)).get("dlqTopic").get("id").asText());

        BulkReplayRequestDto replay = new BulkReplayRequestDto();
        replay.setDlqTopicId(dlqTopicId);
        replay.setInitiatedBy("it-test");
        replay.setMessages(List.of(new BulkReplayRequestDto.MessageIdentifier(0L, 0)));
        replayService.bulkReplayMessages(replay);

        send("/api/notification-channels", """
                {"name": "%s", "type": "SLACK", "configuration": "{\\"webhookUrl\\":\\"https://hooks.slack.com/services/T0/B0/times\\"}"}
                """.formatted(uniqueTopic("#times")), 200);
        UUID ruleId = UUID.fromString(objectMapper.readTree(send("/api/alert-rules", """
                {"name": "%s", "dlqTopicId": "%s", "alertType": "THRESHOLD", "threshold": 1000000}
                """.formatted(uniqueTopic("rule"), dlqTopicId), 200)).get("alertRule").get("id").asText());

        AlertRule rule = alertRuleRepository.findById(ruleId).orElseThrow();
        AlertEvent event = new AlertEvent();
        event.setAlertRule(rule);
        event.setStatus(AlertStatus.SNOOZED);
        event.setMessageCount(5L);
        event.setTriggeredAt(LocalDateTime.now());
        event.setAcknowledgedAt(LocalDateTime.now());
        event.setSnoozedUntil(LocalDateTime.now().plusHours(1));
        alertEventRepository.save(event);

        for (String url : List.of("/api/dlq-topics", "/api/replay/history", "/api/replay/history/dlq/" + dlqTopicId,
                "/api/alert-rules", "/api/alert-events", "/api/notification-channels", "/api/activity",
                "/api/dlq-topics/" + dlqTopicId + "/messages", "/api/dlq-topics/" + dlqTopicId + "/trend")) {
            String body = mockMvc.perform(get(url).with(httpBasic("viewer", "viewer")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            Matcher times = TIME.matcher(body);
            int found = 0;
            while (times.find()) {
                found++;
                assertThat(times.group(1)).as("a time in %s", url).endsWith("Z");
            }
            assertThat(found).as("times found in %s", url).isPositive();
        }
    }

    private String send(String url, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post(url).with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
