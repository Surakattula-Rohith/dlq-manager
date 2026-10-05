package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.entity.DlqCountSample;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.repository.DlqCountSampleRepository;
import com.dlqmanager.repository.DlqTopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trend chart data through the API, from samples stored in the database
 */
@AutoConfigureMockMvc
class DlqTrendIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DlqTopicRepository dlqTopicRepository;

    @Autowired
    private DlqCountSampleRepository dlqCountSampleRepository;

    @Test
    void anySignedInUserGetsHourlyPointsForTheLastDay() throws Exception {
        UUID id = register(uniqueTopic("orders-dlq"));
        LocalDateTime thisHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        store(id, thisHour.minusHours(1).plusMinutes(10), 40, 100);
        store(id, thisHour.minusHours(1).plusMinutes(50), 46, 106);
        store(id, thisHour.plusSeconds(30), 47, 107);

        JsonNode trend = trend(id, "24h");

        assertThat(trend.get("range").asText()).isEqualTo("24h");
        assertThat(trend.get("bucketMinutes").asInt()).isEqualTo(60);
        JsonNode points = trend.get("points");
        assertThat(points).hasSize(24);
        assertThat(points.get(0).get("pending").isNull()).isTrue();

        JsonNode lastHour = points.get(22);
        assertThat(lastHour.get("pending").asLong()).isEqualTo(46);
        assertThat(lastHour.get("newMessages").asLong()).isEqualTo(6);
        assertThat(points.get(23).get("newMessages").asLong()).isEqualTo(1);
        assertThat(points.get(23).get("time").asText()).endsWith(":00:00Z");
    }

    @Test
    void sevenDaysHasTwentyEightPoints() throws Exception {
        UUID id = register(uniqueTopic("orders-dlq"));

        JsonNode trend = trend(id, "7d");

        assertThat(trend.get("bucketMinutes").asInt()).isEqualTo(360);
        assertThat(trend.get("points")).hasSize(28);
    }

    @Test
    void unknownRangeOrTopicIsRejected() throws Exception {
        UUID id = register(uniqueTopic("orders-dlq"));

        mockMvc.perform(get("/api/dlq-topics/" + id + "/trend?range=30d").with(httpBasic("viewer", "viewer")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dlq-topics/" + UUID.randomUUID() + "/trend").with(httpBasic("viewer", "viewer")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/dlq-topics/" + id + "/trend"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode trend(UUID id, String range) throws Exception {
        String json = mockMvc.perform(get("/api/dlq-topics/" + id + "/trend?range=" + range)
                        .with(httpBasic("viewer", "viewer")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private void store(UUID topicId, LocalDateTime at, long pending, long endOffsetSum) {
        DlqCountSample sample = new DlqCountSample();
        sample.setDlqTopicId(topicId);
        sample.setSampledAt(at);
        sample.setPendingCount(pending);
        sample.setEndOffsetSum(endOffsetSum);
        dlqCountSampleRepository.save(sample);
    }

    /**
     * Registered as PAUSED, so the scheduler (running in this shared context) doesn't add samples of its own
     */
    private UUID register(String name) {
        DlqTopic topic = new DlqTopic();
        topic.setDlqTopicName(name);
        topic.setSourceTopic(name.replace("-dlq", ""));
        topic.setDetectionType(DetectionType.MANUAL);
        topic.setStatus(DlqStatus.PAUSED);
        return dlqTopicRepository.save(topic).getId();
    }
}
