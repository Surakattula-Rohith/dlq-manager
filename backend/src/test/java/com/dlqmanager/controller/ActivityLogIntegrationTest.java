package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who did what ends up in the activity log, and the log can be filtered
 */
@AutoConfigureMockMvc
class ActivityLogIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signInsAndFailedSignInsAreRecorded() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/auth/login").param("username", "viewer").param("password", "viewer")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").param("username", "mallory").param("password", "guess")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());

        assertThat(activity("username=viewer&action=SIGNED_IN").get("pagination").get("totalItems").asLong())
                .isPositive();
        assertThat(activity("username=mallory&action=SIGN_IN_FAILED").get("pagination").get("totalItems").asLong())
                .isPositive();
    }

    @Test
    void changesAreRecordedUnderTheUserWhoMadeThem() throws Exception {
        String channelName = uniqueTopic("team-alerts");
        String body = """
                {"name": "%s", "type": "SLACK", "configuration": "{\\"webhookUrl\\":\\"https://hooks.slack.com/services/T0/B0/abcd\\"}"}
                """.formatted(channelName);

        mockMvc.perform(post("/api/notification-channels").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        JsonNode entry = findEntry(activity("username=admin&action=CHANNEL_CREATED&size=100"), channelName);
        assertThat(entry).isNotNull();
        assertThat(entry.get("details").asText()).isEqualTo("SLACK");
    }

    @Test
    void replaysAreRecordedWithTheirOutcome() throws Exception {
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        createTopic(source, 1);
        createTopic(dlq, 1);
        produce(dlq, 0, 2, Map.of("X-Error-Message", "DB Connection Timeout"));

        String register = """
                {"dlqTopicName": "%s", "sourceTopic": "%s", "detectionType": "MANUAL"}
                """.formatted(dlq, source);
        String created = mockMvc.perform(post("/api/dlq-topics").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String dlqTopicId = objectMapper.readTree(created).get("dlqTopic").get("id").asText();

        String replay = """
                {"dlqTopicId": "%s", "messages": [{"partition": 0, "offset": 0}, {"partition": 0, "offset": 1}]}
                """.formatted(dlqTopicId);
        mockMvc.perform(post("/api/replay/bulk").with(httpBasic("operator", "operator"))
                        .contentType(MediaType.APPLICATION_JSON).content(replay))
                .andExpect(status().isOk());

        JsonNode added = findEntry(activity("username=admin&action=DLQ_TOPIC_ADDED&size=100"), dlq);
        assertThat(added).isNotNull();
        assertThat(added.get("details").asText()).contains("source: " + source);

        JsonNode replayed = findEntry(activity("username=operator&action=MESSAGES_REPLAYED&size=100"), dlq);
        assertThat(replayed).isNotNull();
        assertThat(replayed.get("details").asText()).isEqualTo("2 message(s): 2 succeeded, 0 failed");
    }

    @Test
    void testReplaysAreRecordedAsTests() throws Exception {
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        String testTopic = source + "-test";
        createTopic(source, 1);
        createTopic(dlq, 1);
        createTopic(testTopic, 1);
        produce(dlq, 0, 1, Map.of("X-Error-Message", "DB Connection Timeout"));
        String created = mockMvc.perform(post("/api/dlq-topics").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dlqTopicName": "%s", "sourceTopic": "%s", "detectionType": "MANUAL"}
                                """.formatted(dlq, source)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String dlqTopicId = objectMapper.readTree(created).get("dlqTopic").get("id").asText();

        String response = mockMvc.perform(post("/api/replay/bulk").with(httpBasic("operator", "operator"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dlqTopicId": "%s", "targetTopic": "%s", "messages": [{"partition": 0, "offset": 0}]}
                                """.formatted(dlqTopicId, testTopic)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode job = objectMapper.readTree(response).get("replayJob");
        assertThat(job.get("testReplay").asBoolean()).isTrue();
        assertThat(job.get("targetTopic").asText()).isEqualTo(testTopic);

        JsonNode replayed = findEntry(activity("username=operator&action=MESSAGES_REPLAYED&size=100"), dlq);
        assertThat(replayed.get("details").asText())
                .isEqualTo("test replay to " + testTopic + ": 1 message(s): 1 succeeded, 0 failed");

        // A topic that doesn't exist is refused with a reason
        String refused = mockMvc.perform(post("/api/replay/bulk").with(httpBasic("operator", "operator"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dlqTopicId": "%s", "targetTopic": "no-such-topic-%s", "messages": [{"partition": 0, "offset": 0}]}
                                """.formatted(dlqTopicId, source)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(refused).contains("doesn't exist in Kafka");
    }

    @Test
    void everyRoleCanReadTheLogPageByPage() throws Exception {
        JsonNode page = activity("size=1");

        assertThat(page.get("activity")).hasSizeLessThanOrEqualTo(1);
        assertThat(page.get("pagination").get("pageSize").asInt()).isEqualTo(1);
        assertThat(page.get("usernames").isArray()).isTrue();

        mockMvc.perform(get("/api/activity").with(httpBasic("viewer", "viewer")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/activity?action=NOT_AN_ACTION").with(httpBasic("viewer", "viewer")))
                .andExpect(status().isBadRequest());
    }

    private JsonNode activity(String query) throws Exception {
        String json = mockMvc.perform(get("/api/activity?" + query).with(httpBasic("viewer", "viewer")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private static JsonNode findEntry(JsonNode page, String target) {
        return StreamSupport.stream(page.get("activity").spliterator(), false)
                .filter(entry -> target.equals(entry.get("target").asText()))
                .findFirst()
                .orElse(null);
    }

    private Cookie xsrfCookie() throws Exception {
        return Arrays.stream(mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookies())
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .findFirst()
                .orElseThrow();
    }
}
