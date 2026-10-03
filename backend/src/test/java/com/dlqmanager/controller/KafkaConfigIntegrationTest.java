package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.entity.KafkaConfig;
import com.dlqmanager.model.enums.KafkaAuthentication;
import com.dlqmanager.repository.ActivityEventRepository;
import com.dlqmanager.repository.KafkaConfigRepository;
import com.dlqmanager.service.KafkaConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saving Kafka security settings: the password is kept safe and never handed back
 */
@AutoConfigureMockMvc
class KafkaConfigIntegrationTest extends IntegrationTestBase {

    private static final String BROKERS = "broker1.internal:9093";
    private static final String PASSWORD = "kafka-s3cret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaConfigRepository kafkaConfigRepository;

    @Autowired
    private KafkaConfigService kafkaConfigService;

    @Autowired
    private ActivityEventRepository activityEventRepository;

    /**
     * Other test classes share this Spring context and expect the default test Kafka
     */
    @AfterEach
    void backToTheDefaultConnection() {
        kafkaConfigRepository.deleteAll();
    }

    @Test
    void passwordIsStoredEncryptedAndNeverSentBack() throws Exception {
        String saveResponse = save(secured(BROKERS, "dlq-user", PASSWORD))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String getResponse = mockMvc.perform(get("/api/kafka/config").with(httpBasic("viewer", "viewer")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        for (String response : new String[]{saveResponse, getResponse}) {
            JsonNode json = objectMapper.readTree(response);
            assertThat(response).doesNotContain(PASSWORD);
            assertThat(json.has("password")).isFalse();
            assertThat(json.get("passwordSet").asBoolean()).isTrue();
            assertThat(json.get("authentication").asText()).isEqualTo("SCRAM_SHA_512");
            assertThat(json.get("encrypted").asBoolean()).isTrue();
            assertThat(json.get("username").asText()).isEqualTo("dlq-user");
            assertThat(json.get("configured").asBoolean()).isTrue();
        }

        // In the database: encrypted, not readable
        KafkaConfig stored = kafkaConfigRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(stored.getPasswordEncrypted()).startsWith("enc:v1:").doesNotContain(PASSWORD);
        // ...but the app can still use it to connect
        assertThat(kafkaConfigService.getConnection().password()).isEqualTo(PASSWORD);

        // The activity log says what changed, without the password
        assertThat(activityEventRepository.findAll())
                .filteredOn(event -> "Kafka connection".equals(event.getTarget()))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.getDetails()).doesNotContain(PASSWORD))
                .anySatisfy(event -> assertThat(event.getDetails())
                        .endsWith("-> " + BROKERS + " (SCRAM-SHA-512 login as dlq-user, encrypted)"));
    }

    @Test
    void savedPasswordIsReusedWhenLeftOut() throws Exception {
        save(secured(BROKERS, "dlq-user", PASSWORD)).andExpect(status().isOk());

        // Same brokers and username, only the certificate changed - no need to type the password again
        save("""
                {"bootstrapServers": "%s", "authentication": "SCRAM_SHA_512", "encrypted": true,
                 "username": "dlq-user", "caCertificate": "-----BEGIN CERTIFICATE-----\\nMIIB\\n-----END CERTIFICATE-----"}
                """.formatted(BROKERS)).andExpect(status().isOk());

        assertThat(kafkaConfigService.getConnection().password()).isEqualTo(PASSWORD);
        assertThat(kafkaConfigService.getConnection().caCertificate()).startsWith("-----BEGIN CERTIFICATE-----");
    }

    @Test
    void savedPasswordIsNeverSentToADifferentAddressOrUser() throws Exception {
        save(secured(BROKERS, "dlq-user", PASSWORD)).andExpect(status().isOk());

        String otherBrokers = save("""
                {"bootstrapServers": "evil.example.com:9092", "authentication": "PLAIN", "encrypted": false, "username": "dlq-user"}
                """).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String otherUser = save("""
                {"bootstrapServers": "%s", "authentication": "SCRAM_SHA_512", "encrypted": true, "username": "someone-else"}
                """.formatted(BROKERS)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        // The same goes for a request that only changes the address (what the page sent before security existed)
        save("""
                {"bootstrapServers": "evil.example.com:9092"}
                """).andExpect(status().isBadRequest());

        assertThat(otherBrokers).contains("Enter the Kafka password again");
        assertThat(otherUser).contains("Enter the Kafka password again");
        assertThat(kafkaConfigService.getBootstrapServers()).isEqualTo(BROKERS);

        // The connection test follows the same rule
        String test = mockMvc.perform(post("/api/kafka/config/test").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"bootstrapServers": "evil.example.com:9092", "authentication": "PLAIN", "username": "dlq-user"}
                                """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(test).get("success").asBoolean()).isFalse();
        assertThat(test).contains("Enter the Kafka password again");
    }

    @Test
    void requestWithOnlyTheAddressKeepsTheSecuritySettings() throws Exception {
        save(secured(BROKERS, "dlq-user", PASSWORD)).andExpect(status().isOk());

        save("""
                {"bootstrapServers": "%s"}
                """.formatted(BROKERS)).andExpect(status().isOk());

        assertThat(kafkaConfigService.getConnection().authentication()).isEqualTo(KafkaAuthentication.SCRAM_SHA_512);
        assertThat(kafkaConfigService.getConnection().password()).isEqualTo(PASSWORD);
    }

    @Test
    void switchingToNoLoginForgetsTheUsernameAndPassword() throws Exception {
        save(secured(BROKERS, "dlq-user", PASSWORD)).andExpect(status().isOk());

        String response = save("""
                {"bootstrapServers": "%s", "authentication": "NONE", "encrypted": false}
                """.formatted(BROKERS)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("passwordSet").asBoolean()).isFalse();
        assertThat(json.get("username").isNull()).isTrue();
        KafkaConfig stored = kafkaConfigRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(stored.getPasswordEncrypted()).isNull();
        assertThat(stored.getUsername()).isNull();
    }

    @Test
    void loginWithoutUsernameOrPasswordIsRejected() throws Exception {
        String noUsername = save("""
                {"bootstrapServers": "%s", "authentication": "PLAIN", "encrypted": true, "password": "x"}
                """.formatted(BROKERS)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String noPassword = save("""
                {"bootstrapServers": "%s", "authentication": "PLAIN", "encrypted": true, "username": "dlq-user"}
                """.formatted(BROKERS)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat(noUsername).contains("A username is required");
        assertThat(noPassword).contains("A password is required");
        assertThat(kafkaConfigRepository.count()).isZero();
    }

    @Test
    void settingsCanBeOpenedAndFixedWhenTheSavedPasswordCannotBeRead() throws Exception {
        // As if DLQ_SECRET_KEY was changed after the password was saved
        save(secured(BROKERS, "dlq-user", PASSWORD)).andExpect(status().isOk());
        KafkaConfig stored = kafkaConfigRepository.findFirstByOrderByIdAsc().orElseThrow();
        stored.setPasswordEncrypted("enc:v1:00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        kafkaConfigRepository.save(stored);

        // Connecting fails with a message that says what to do...
        assertThatThrownBy(() -> kafkaConfigService.getConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Enter the password again in Settings");
        // ...the Settings page still loads...
        mockMvc.perform(get("/api/kafka/config").with(httpBasic("admin", "admin"))).andExpect(status().isOk());
        // ...and typing the password again repairs it
        save(secured(BROKERS, "dlq-user", "new-password")).andExpect(status().isOk());
        assertThat(kafkaConfigService.getConnection().password()).isEqualTo("new-password");
    }

    @Test
    void onlyAdminsCanChangeTheConnection() throws Exception {
        mockMvc.perform(put("/api/kafka/config").with(httpBasic("operator", "operator"))
                        .contentType(MediaType.APPLICATION_JSON).content(secured(BROKERS, "dlq-user", PASSWORD)))
                .andExpect(status().isForbidden());

        assertThat(kafkaConfigRepository.count()).isZero();
    }

    private ResultActions save(String body) throws Exception {
        return mockMvc.perform(put("/api/kafka/config").with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String secured(String brokers, String username, String password) {
        return """
                {"bootstrapServers": "%s", "authentication": "SCRAM_SHA_512", "encrypted": true,
                 "username": "%s", "password": "%s"}
                """.formatted(brokers, username, password);
    }
}
