package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.repository.DlqTopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WithMockUser
class MessageExportIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DlqTopicRepository dlqTopicRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportsFilteredMessagesAsCsv() throws Exception {
        UUID id = topicWithMessages();

        String csv = download("/api/dlq-topics/" + id + "/messages/export?format=csv&errorType=Validation Failed",
                "text/csv");

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(1 + 2); // header + the 2 "Validation Failed" messages
        assertThat(lines[1]).contains("Validation Failed");
        assertThat(lines[2]).contains("Validation Failed");
    }

    @Test
    void exportsEverythingAsJson() throws Exception {
        UUID id = topicWithMessages();

        String json = download("/api/dlq-topics/" + id + "/messages/export?format=json", "application/json");

        JsonNode messages = objectMapper.readTree(json);
        assertThat(messages).hasSize(5);
        assertThat(messages.get(0).has("payload")).isTrue();
    }

    @Test
    void rejectsUnknownFormat() throws Exception {
        UUID id = topicWithMessages();

        mockMvc.perform(get("/api/dlq-topics/" + id + "/messages/export?format=xml"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTopicIsNotFound() throws Exception {
        mockMvc.perform(get("/api/dlq-topics/" + UUID.randomUUID() + "/messages/export"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAndExportShareTheTimeWindow() throws Exception {
        String dlq = uniqueTopic("orders-dlq");
        createTopic(dlq, 1);
        Instant noon = Instant.parse("2026-10-05T12:00:00Z");
        produceAt(dlq, 0, "before", noon.minusSeconds(7200), Map.of("X-Error-Message", "DB Connection Timeout"));
        produceAt(dlq, 0, "during-1", noon.plusSeconds(300), Map.of("X-Error-Message", "DB Connection Timeout"));
        produceAt(dlq, 0, "during-2", noon.plusSeconds(900), Map.of("X-Error-Message", "DB Connection Timeout"));
        produceAt(dlq, 0, "after", noon.plusSeconds(7200), Map.of("X-Error-Message", "DB Connection Timeout"));
        UUID id = register(dlq);
        String window = "from=2026-10-05T12:00:00Z&to=2026-10-05T13:00:00Z";

        String list = mockMvc.perform(get("/api/dlq-topics/" + id + "/messages?" + window))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode page = objectMapper.readTree(list);
        assertThat(page.get("pagination").get("filtered").asBoolean()).isTrue();
        assertThat(page.get("pagination").get("matchingMessages").asLong()).isEqualTo(2);
        assertThat(page.get("pagination").get("totalMessages").asLong()).isEqualTo(4);
        assertThat(page.get("messages")).extracting(message -> message.get("messageKey").asText())
                .containsExactly("during-1", "during-2");

        String csv = download("/api/dlq-topics/" + id + "/messages/export?format=csv&" + window, "text/csv");
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(1 + 2);
        assertThat(lines[1]).contains("during-1");
        assertThat(lines[2]).contains("during-2");
    }

    @Test
    void timeWindowThatMakesNoSenseIsRejected() throws Exception {
        UUID id = topicWithMessages();

        String notATime = mockMvc.perform(get("/api/dlq-topics/" + id + "/messages?from=yesterday"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(notATime).contains("from must be a time like");

        mockMvc.perform(get("/api/dlq-topics/" + id + "/messages?from=2026-10-05T13:00:00Z&to=2026-10-05T12:00:00Z"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dlq-topics/" + id + "/messages/export?to=noon"))
                .andExpect(status().isBadRequest());
    }

    private String download(String url, String expectedType) throws Exception {
        MvcResult started = mockMvc.perform(get(url))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult finished = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andReturn();

        assertThat(finished.getResponse().getContentType()).startsWith(expectedType);
        return finished.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private UUID topicWithMessages() throws Exception {
        String dlq = uniqueTopic("orders-dlq");
        createTopic(dlq, 1);
        produce(dlq, 0, 3, Map.of("X-Error-Message", "DB Connection Timeout"));
        produce(dlq, 0, 2, Map.of("X-Error-Message", "Validation Failed"));
        return register(dlq);
    }

    private UUID register(String dlq) {
        DlqTopic topic = new DlqTopic();
        topic.setDlqTopicName(dlq);
        topic.setSourceTopic("orders");
        topic.setDetectionType(DetectionType.MANUAL);
        topic.setStatus(DlqStatus.ACTIVE);
        return dlqTopicRepository.save(topic).getId();
    }
}
