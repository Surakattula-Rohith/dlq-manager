package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-in, sessions and CSRF, done the way the browser does it:
 * read the XSRF-TOKEN cookie and send it back in the X-XSRF-TOKEN header.
 * (spring-security-test's csrf() helper is avoided on purpose - it swaps the cookie
 * repository for a test one, which would hide a missing cookie.)
 */
@AutoConfigureMockMvc
class AuthIntegrationTest extends IntegrationTestBase {

    // Invalid on purpose (no configuration): reaching the controller gives 400,
    // being stopped by security gives 401 or 403
    private static final String INCOMPLETE_CHANNEL = "{\"name\":\"team\",\"type\":\"SLACK\"}";

    @Autowired
    private MockMvc mockMvc;

    private record SignedIn(MockHttpSession session, Cookie xsrf) {
    }

    @Test
    void apiNeedsSignIn() throws Exception {
        mockMvc.perform(get("/api/dlq-topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReportsSignedOutUserAndHandsOutCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.demoAccounts").value(true))
                .andReturn();

        assertThat(xsrfCookie(result.getResponse())).isNotNull();
    }

    @Test
    void signInKeepsUserInSession() throws Exception {
        SignedIn user = signIn("operator", "operator");

        mockMvc.perform(get("/api/auth/me").session(user.session()))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.role").value("OPERATOR"));

        mockMvc.perform(get("/api/dlq-topics").session(user.session()))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "not-the-password")
                        .with(xsrf(freshXsrfCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Wrong username or password"));
    }

    @Test
    void loginNeedsCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionRequestsNeedCsrfToken() throws Exception {
        SignedIn admin = signIn("admin", "admin");

        mockMvc.perform(post("/api/notification-channels").session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON).content(INCOMPLETE_CHANNEL))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/notification-channels").session(admin.session()).with(xsrf(admin.xsrf()))
                        .contentType(MediaType.APPLICATION_JSON).content(INCOMPLETE_CHANNEL))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scriptsCanUseHttpBasicWithoutCsrfToken() throws Exception {
        mockMvc.perform(get("/api/dlq-topics").with(httpBasic("viewer", "viewer")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/notification-channels").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(INCOMPLETE_CHANNEL))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dlq-topics").with(httpBasic("viewer", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signOutEndsSession() throws Exception {
        SignedIn viewer = signIn("viewer", "viewer");

        mockMvc.perform(post("/api/auth/logout").session(viewer.session()).with(xsrf(viewer.xsrf())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/dlq-topics").session(viewer.session()))
                .andExpect(status().isUnauthorized());
    }

    private SignedIn signIn(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .param("username", username)
                        .param("password", password)
                        .with(xsrf(freshXsrfCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        // Login replaces the CSRF token, so the response must carry the new cookie
        Cookie newToken = xsrfCookie(result.getResponse());
        assertThat(newToken).isNotNull();

        return new SignedIn((MockHttpSession) result.getRequest().getSession(false), newToken);
    }

    private Cookie freshXsrfCookie() throws Exception {
        Cookie cookie = xsrfCookie(mockMvc.perform(get("/api/auth/me")).andReturn().getResponse());
        assertThat(cookie).isNotNull();
        return cookie;
    }

    /**
     * The XSRF-TOKEN cookie set by a response (ignoring a cookie that only deletes the old token)
     */
    private static Cookie xsrfCookie(MockHttpServletResponse response) {
        return Arrays.stream(response.getCookies())
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()) && cookie.getMaxAge() != 0)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private static RequestPostProcessor xsrf(Cookie token) {
        return request -> {
            request.setCookies(token);
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }
}
