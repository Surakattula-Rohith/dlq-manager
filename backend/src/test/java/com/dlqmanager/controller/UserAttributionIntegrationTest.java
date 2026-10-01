package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.entity.AlertRule;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.AlertStatus;
import com.dlqmanager.model.enums.AlertType;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.repository.AlertEventRepository;
import com.dlqmanager.repository.AlertRuleRepository;
import com.dlqmanager.repository.DlqTopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replays and alert actions are recorded under the signed-in user
 */
@AutoConfigureMockMvc
class UserAttributionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DlqTopicRepository dlqTopicRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @Test
    void replayIsRecordedUnderSignedInUserNotTheNameInTheRequest() throws Exception {
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        createTopic(source, 1);
        createTopic(dlq, 1);
        produce(dlq, 0, 1, Map.of("X-Error-Message", "DB Connection Timeout"));
        DlqTopic topic = registerDlq(dlq, source);

        String body = """
                {"dlqTopicId": "%s", "messageOffset": 0, "messagePartition": 0, "initiatedBy": "someone-else"}
                """.formatted(topic.getId());

        mockMvc.perform(post("/api/replay/single").with(httpBasic("operator", "operator"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayJob.initiatedBy").value("operator"));
    }

    @Test
    void acknowledgeAndSnoozeRecordWhoDidIt() throws Exception {
        AlertRule rule = alertRule();
        UUID acknowledged = firingEvent(rule).getId();
        UUID snoozed = firingEvent(rule).getId();

        mockMvc.perform(post("/api/alert-events/" + acknowledged + "/acknowledge").with(httpBasic("operator", "operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertEvent.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.alertEvent.acknowledgedBy").value("operator"));

        mockMvc.perform(post("/api/alert-events/" + snoozed + "/snooze").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minutes\": 30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertEvent.status").value("SNOOZED"))
                .andExpect(jsonPath("$.alertEvent.snoozedBy").value("admin"));
    }

    private DlqTopic registerDlq(String dlq, String source) {
        DlqTopic topic = new DlqTopic();
        topic.setDlqTopicName(dlq);
        topic.setSourceTopic(source);
        topic.setDetectionType(DetectionType.MANUAL);
        topic.setStatus(DlqStatus.ACTIVE);
        return dlqTopicRepository.save(topic);
    }

    private AlertRule alertRule() {
        String dlq = uniqueTopic("payments-dlq");
        AlertRule rule = new AlertRule();
        rule.setName("backlog");
        rule.setDlqTopic(registerDlq(dlq, "payments"));
        rule.setAlertType(AlertType.THRESHOLD);
        rule.setThreshold(1L);
        return alertRuleRepository.save(rule);
    }

    private AlertEvent firingEvent(AlertRule rule) {
        AlertEvent event = new AlertEvent();
        event.setAlertRule(rule);
        event.setStatus(AlertStatus.FIRING);
        event.setMessageCount(1L);
        event.setTriggeredAt(LocalDateTime.now());
        return alertEventRepository.save(event);
    }
}
