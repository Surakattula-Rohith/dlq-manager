package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.repository.NotificationChannelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Team feed end to end: a Slack channel follows some kinds of activity,
 * and what people then do through the API is posted to its webhook.
 *
 * Slack itself is replaced by a stand-in that records the requests the app sends.
 */
@AutoConfigureMockMvc
class TeamFeedIntegrationTest extends IntegrationTestBase {

    private static final String WEBHOOK = "https://hooks.slack.com/services/T0FEED/B0FEED/teamFeedSecret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private NotificationChannelRepository notificationChannelRepository;

    private ClientHttpRequestFactory realRequestFactory;
    private MockRestServiceServer slack;
    private String channelId;

    @BeforeEach
    void replaceSlack() {
        realRequestFactory = restTemplate.getRequestFactory();
        slack = MockRestServiceServer.bindTo(restTemplate).build();
    }

    /**
     * Other test classes share this Spring context: give them back the real HTTP client
     * and remove the channel, so their actions are not posted anywhere
     */
    @AfterEach
    void restore() {
        if (channelId != null) {
            notificationChannelRepository.deleteById(UUID.fromString(channelId));
        }
        restTemplate.setRequestFactory(realRequestFactory);
    }

    @Test
    void replaysAndSetupChangesArePostedToTheChannelThatFollowsThem() throws Exception {
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        createTopic(source, 1);
        createTopic(dlq, 1);
        produce(dlq, 0, 2, Map.of("X-Error-Message", "DB Connection Timeout"));

        // Posts arrive in the order things happened
        expectSlackPost("*admin* added Slack channel `#incidents` - SLACK, team feed: replays, changes");
        expectSlackPost("*admin* added DLQ topic `" + dlq + "` - source: " + source);
        expectSlackPost("*operator* replayed messages from `" + dlq + "` - 2 message(s): 2 succeeded, 0 failed");

        JsonNode channel = createChannel("#incidents", "[\"REPLAYS\", \"CHANGES\"]");
        assertThat(channel.get("activityFeed")).extracting(JsonNode::asText).containsExactly("REPLAYS", "CHANGES");

        String created = mockMvc.perform(post("/api/dlq-topics").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dlqTopicName": "%s", "sourceTopic": "%s", "detectionType": "MANUAL"}
                                """.formatted(dlq, source)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String dlqTopicId = objectMapper.readTree(created).get("dlqTopic").get("id").asText();

        mockMvc.perform(post("/api/replay/bulk").with(httpBasic("operator", "operator"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dlqTopicId": "%s", "messages": [{"partition": 0, "offset": 0}, {"partition": 0, "offset": 1}]}
                                """.formatted(dlqTopicId)))
                .andExpect(status().isOk());

        // Sent in the background, so wait for all three to arrive
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> slack.verify());
    }

    @Test
    void aChannelThatFollowsNothingGetsNoPosts() throws Exception {
        slack.expect(never(), requestTo(WEBHOOK));

        // Creating the channel is itself a setup change - but this channel doesn't follow those
        createChannel("#alerts-only", "[]");

        // Give the background thread time to (wrongly) post before checking that it didn't
        Thread.sleep(1000);
        slack.verify();
    }

    @Test
    void savingTheChannelWithoutTheFieldKeepsWhatItFollows() throws Exception {
        expectSlackPost("added Slack channel `#platform`");
        expectSlackPost("updated Slack channel `#platform-team` - enabled, team feed: changes");
        createChannel("#platform", "[\"CHANGES\"]");

        // What the Settings page sent before the team feed existed: no activityFeed, masked webhook
        String updated = mockMvc.perform(put("/api/notification-channels/" + channelId).with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "#platform-team", "type": "SLACK", "enabled": true,
                                 "configuration": "{\\"webhookUrl\\":\\"https://hooks.slack.com/****cret\\"}"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(objectMapper.readTree(updated).get("channel").get("activityFeed"))
                .extracting(JsonNode::asText).containsExactly("CHANGES");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> slack.verify());
    }

    @Test
    void unknownCategoryIsRejectedWithAMessageThatListsTheChoices() throws Exception {
        String response = mockMvc.perform(post("/api/notification-channels").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(channelBody("#typo", "[\"REPLAY\"]")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(response).contains("Use REPLAYS, ALERTS or CHANGES");
    }

    private void expectSlackPost(String text) {
        slack.expect(once(), requestTo(WEBHOOK))
                .andExpect(jsonPath("$.text", containsString(text)))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
    }

    private JsonNode createChannel(String name, String activityFeed) throws Exception {
        String response = mockMvc.perform(post("/api/notification-channels").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(channelBody(name, activityFeed)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode channel = objectMapper.readTree(response).get("channel");
        channelId = channel.get("id").asText();
        return channel;
    }

    private static String channelBody(String name, String activityFeed) {
        return """
                {"name": "%s", "type": "SLACK", "activityFeed": %s,
                 "configuration": "{\\"webhookUrl\\":\\"%s\\"}"}
                """.formatted(name, activityFeed, WEBHOOK);
    }
}
