package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may do what. Requests are sent with deliberately incomplete bodies or unknown ids:
 * the point is only whether security lets them through to the controller (anything but 401/403).
 */
@AutoConfigureMockMvc
class RoleAccessIntegrationTest extends IntegrationTestBase {

    private static final String INCOMPLETE_CHANNEL = "{\"name\":\"team\",\"type\":\"SLACK\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyRoleCanLook() throws Exception {
        for (String user : new String[]{"viewer", "operator", "admin"}) {
            mockMvc.perform(get("/api/dlq-topics").with(httpBasic(user, user)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/replay/history").with(httpBasic(user, user)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void viewerCannotReplayOrHandleAlerts() throws Exception {
        perform(replay(), "viewer")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Your role is not allowed to do this"));
        perform(post("/api/alert-events/" + UUID.randomUUID() + "/acknowledge"), "viewer")
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReplayAndHandleAlerts() throws Exception {
        assertAllowed(perform(replay(), "operator"));
        assertAllowed(perform(post("/api/alert-events/" + UUID.randomUUID() + "/acknowledge"), "operator"));
        assertAllowed(perform(post("/api/alert-events/" + UUID.randomUUID() + "/snooze")
                .contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":30}"), "operator"));
    }

    @Test
    void operatorCannotChangeConfiguration() throws Exception {
        perform(createChannel(), "operator").andExpect(status().isForbidden());
        perform(put("/api/kafka/config").contentType(MediaType.APPLICATION_JSON).content("{}"), "operator")
                .andExpect(status().isForbidden());
        perform(delete("/api/dlq-topics/" + UUID.randomUUID()), "operator").andExpect(status().isForbidden());
        perform(delete("/api/alert-rules/" + UUID.randomUUID()), "operator").andExpect(status().isForbidden());
    }

    @Test
    void adminCanDoEverything() throws Exception {
        perform(createChannel(), "admin").andExpect(status().isBadRequest());
        assertAllowed(perform(replay(), "admin"));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String user) throws Exception {
        return mockMvc.perform(request.with(httpBasic(user, user)));
    }

    private static MockHttpServletRequestBuilder replay() {
        return post("/api/replay/single").contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    private static MockHttpServletRequestBuilder createChannel() {
        return post("/api/notification-channels").contentType(MediaType.APPLICATION_JSON).content(INCOMPLETE_CHANNEL);
    }

    private static void assertAllowed(ResultActions result) {
        assertThat(result.andReturn().getResponse().getStatus()).isNotIn(401, 403);
    }
}
