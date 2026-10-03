package com.dlqmanager.config;

import com.dlqmanager.model.enums.KafkaAuthentication;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;

import java.util.HashMap;
import java.util.Map;

/**
 * How to reach the Kafka cluster
 *
 * Every Kafka client the app creates starts from clientProperties():
 * - the consumers that browse DLQ messages (KafkaConsumerPool)
 * - the producer that replays them (KafkaProducerConfig)
 * - the admin client that lists topics (KafkaAdminService)
 * - the connection test on the Settings page (KafkaConfigService)
 *
 * So a connection setting only has to be added here to apply everywhere.
 *
 * It is a record: two values are equal when they describe the same connection. The
 * long-lived clients remember the value they were built from and rebuild themselves
 * when the one saved in Settings is no longer equal to it (a new password included).
 *
 * @param bootstrapServers Kafka brokers, e.g. "localhost:9092" or "broker1:9092,broker2:9092"
 * @param authentication   how the app signs in (NONE, PLAIN or SCRAM)
 * @param encrypted        true to talk to the brokers over TLS
 * @param username         login name, only when authentication needs a login
 * @param password         login password, only when authentication needs a login
 * @param caCertificate    the certificate (PEM text) of the authority that signed the brokers'
 *                         certificates. Only needed when that is a company-internal authority;
 *                         certificates from public authorities are trusted already.
 */
public record KafkaConnection(String bootstrapServers,
                              KafkaAuthentication authentication,
                              boolean encrypted,
                              String username,
                              String password,
                              String caCertificate) {

    public KafkaConnection {
        bootstrapServers = bootstrapServers.trim();
        authentication = authentication == null ? KafkaAuthentication.NONE : authentication;
        // Settings that don't apply are dropped, so they can't make two equal connections look different
        username = authentication.needsLogin() ? blankToNull(username) : null;
        // (a password is kept exactly as typed - spaces can be part of it)
        password = authentication.needsLogin() && password != null && !password.isEmpty() ? password : null;
        caCertificate = encrypted ? blankToNull(caCertificate) : null;
    }

    /**
     * A cluster with no login and no encryption (local development)
     */
    public KafkaConnection(String bootstrapServers) {
        this(bootstrapServers, KafkaAuthentication.NONE, false, null, null, null);
    }

    /**
     * Connection properties shared by consumers, producers and admin clients.
     * A new map every time, so the caller can add its own client settings to it.
     */
    public Map<String, Object> clientProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol());

        if (authentication.needsLogin()) {
            props.put(SaslConfigs.SASL_MECHANISM, authentication.saslMechanism());
            props.put(SaslConfigs.SASL_JAAS_CONFIG, authentication.loginModule() + " required"
                    + " username=\"" + escape(username) + "\""
                    + " password=\"" + escape(password) + "\";");
        }

        if (caCertificate != null) {
            // Trust the company's own certificate authority, given as PEM text (no keystore file needed)
            props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
            props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, caCertificate);
        }

        return props;
    }

    /**
     * Kafka's name for the combination of login and encryption:
     * PLAINTEXT (neither), SSL (encrypted), SASL_PLAINTEXT (login), SASL_SSL (both)
     */
    public String securityProtocol() {
        if (authentication.needsLogin()) {
            return encrypted ? "SASL_SSL" : "SASL_PLAINTEXT";
        }
        return encrypted ? "SSL" : "PLAINTEXT";
    }

    /**
     * Short description for logs and the activity page, e.g. "SCRAM-SHA-512 login as dlq-user, encrypted"
     */
    public String describeSecurity() {
        String login = authentication.needsLogin()
                ? authentication.saslMechanism() + " login as " + username
                : "no login";
        return login + (encrypted ? ", encrypted" : ", not encrypted");
    }

    /**
     * Connections end up in log lines, so the password and certificate are left out
     */
    @Override
    public String toString() {
        return bootstrapServers + " (" + describeSecurity() + ")";
    }

    /**
     * The login settings are written as: username="..." password="...";
     * so a quote or backslash inside a value has to be escaped.
     */
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
