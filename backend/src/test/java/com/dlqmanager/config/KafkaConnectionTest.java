package com.dlqmanager.config;

import com.dlqmanager.model.enums.KafkaAuthentication;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.types.Password;
import org.apache.kafka.common.security.JaasContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConnectionTest {

    private static final String CERTIFICATE = "-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----";

    @Test
    void clientPropertiesPointAtTheCluster() {
        KafkaConnection connection = new KafkaConnection("broker1:9092,broker2:9092");

        assertThat(connection.clientProperties())
                .containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092,broker2:9092");
    }

    @Test
    void sameAddressIsTheSameConnectionSoClientsAreNotRebuilt() {
        assertThat(new KafkaConnection(" kafka:9092 ")).isEqualTo(new KafkaConnection("kafka:9092"));
        assertThat(new KafkaConnection("kafka-a:9092")).isNotEqualTo(new KafkaConnection("kafka-b:9092"));
    }

    @Test
    void everyClientGetsItsOwnCopyOfTheProperties() {
        KafkaConnection connection = new KafkaConnection("kafka:9092");

        Map<String, Object> consumerProps = connection.clientProperties();
        consumerProps.put("group.id", "browser");

        assertThat(connection.clientProperties()).doesNotContainKey("group.id");
    }

    @Test
    void withoutSecurityNoLoginOrCertificateSettingsAreSent() {
        Map<String, Object> props = new KafkaConnection("kafka:9092").clientProperties();

        assertThat(props).containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
        assertThat(props).doesNotContainKeys(SaslConfigs.SASL_MECHANISM, SaslConfigs.SASL_JAAS_CONFIG,
                SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG);
    }

    @Test
    void loginAndEncryptionDecideTheSecurityProtocol() {
        assertThat(connection(KafkaAuthentication.NONE, false).securityProtocol()).isEqualTo("PLAINTEXT");
        assertThat(connection(KafkaAuthentication.NONE, true).securityProtocol()).isEqualTo("SSL");
        assertThat(connection(KafkaAuthentication.SCRAM_SHA_512, false).securityProtocol()).isEqualTo("SASL_PLAINTEXT");
        assertThat(connection(KafkaAuthentication.SCRAM_SHA_512, true).securityProtocol()).isEqualTo("SASL_SSL");
    }

    @Test
    void passwordLoginUsesTheMatchingKafkaLoginModule() {
        Map<String, Object> plain = connection(KafkaAuthentication.PLAIN, true).clientProperties();
        Map<String, Object> scram = connection(KafkaAuthentication.SCRAM_SHA_256, true).clientProperties();

        assertThat(plain).containsEntry(SaslConfigs.SASL_MECHANISM, "PLAIN");
        assertThat(loginOptions(plain)).containsEntry("username", "dlq-user").containsEntry("password", "s3cret");
        assertThat((String) plain.get(SaslConfigs.SASL_JAAS_CONFIG)).startsWith("org.apache.kafka.common.security.plain.PlainLoginModule required");

        assertThat(scram).containsEntry(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-256");
        assertThat((String) scram.get(SaslConfigs.SASL_JAAS_CONFIG)).startsWith("org.apache.kafka.common.security.scram.ScramLoginModule required");
    }

    @Test
    void passwordWithQuotesBackslashesAndSpacesReachesKafkaUnchanged() {
        String awkward = " p\"a\\ss; word=1 ";
        KafkaConnection connection = new KafkaConnection("kafka:9092", KafkaAuthentication.PLAIN, true,
                "dlq-user", awkward, null);

        // Parsed by Kafka's own login-settings parser, the same one the client uses
        assertThat(loginOptions(connection.clientProperties())).containsEntry("password", awkward);
    }

    @Test
    void companyCertificateIsTrustedAsPemText() {
        KafkaConnection connection = new KafkaConnection("kafka:9093", KafkaAuthentication.NONE, true,
                null, null, CERTIFICATE);

        assertThat(connection.clientProperties())
                .containsEntry(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM")
                .containsEntry(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, CERTIFICATE);
    }

    @Test
    void settingsThatDoNotApplyAreDropped() {
        KafkaConnection noLogin = new KafkaConnection("kafka:9092", KafkaAuthentication.NONE, false,
                "left-over-user", "left-over-password", CERTIFICATE);

        assertThat(noLogin).isEqualTo(new KafkaConnection("kafka:9092"));
    }

    @Test
    void aNewPasswordIsADifferentConnectionSoClientsReconnect() {
        KafkaConnection before = new KafkaConnection("kafka:9092", KafkaAuthentication.PLAIN, true, "dlq-user", "old", null);
        KafkaConnection after = new KafkaConnection("kafka:9092", KafkaAuthentication.PLAIN, true, "dlq-user", "new", null);

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void textFormForLogsNeverShowsThePasswordOrCertificate() {
        KafkaConnection connection = new KafkaConnection("kafka:9093", KafkaAuthentication.SCRAM_SHA_512, true,
                "dlq-user", "s3cret", CERTIFICATE);

        assertThat(connection.toString())
                .isEqualTo("kafka:9093 (SCRAM-SHA-512 login as dlq-user, encrypted)")
                .doesNotContain("s3cret")
                .doesNotContain("CERTIFICATE");
        assertThat(new KafkaConnection("kafka:9092").toString()).isEqualTo("kafka:9092 (no login, not encrypted)");
    }

    private static KafkaConnection connection(KafkaAuthentication authentication, boolean encrypted) {
        return new KafkaConnection("kafka:9092", authentication, encrypted, "dlq-user", "s3cret", null);
    }

    private static Map<String, Object> loginOptions(Map<String, Object> clientProperties) {
        Map<String, Object> configs = Map.of(SaslConfigs.SASL_JAAS_CONFIG,
                new Password((String) clientProperties.get(SaslConfigs.SASL_JAAS_CONFIG)));
        return Map.copyOf(JaasContext.loadClientContext(configs).configurationEntries().get(0).getOptions());
    }
}
