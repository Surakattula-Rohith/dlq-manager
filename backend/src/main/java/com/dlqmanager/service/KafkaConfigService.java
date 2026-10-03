package com.dlqmanager.service;

import com.dlqmanager.config.KafkaConnection;
import com.dlqmanager.config.KafkaStartupProperties;
import com.dlqmanager.model.dto.KafkaConfigRequest;
import com.dlqmanager.model.dto.KafkaConfigView;
import com.dlqmanager.model.entity.KafkaConfig;
import com.dlqmanager.model.enums.ActivityAction;
import com.dlqmanager.repository.KafkaConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.security.cert.CertPathBuilderException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Kafka connection settings
 *
 * Where the settings come from:
 * - Until something is saved from the Settings page: the startup values
 *   (spring.kafka.bootstrap-servers and dlq.kafka.*, usually environment variables)
 * - After that: the row in the kafka_config table
 *
 * The Kafka password is stored encrypted (see SecretCipher) and never leaves the backend:
 * the Settings page is only told whether one is saved.
 */
@Service
@Slf4j
@EnableConfigurationProperties(KafkaStartupProperties.class)
public class KafkaConfigService {

    private final KafkaConfigRepository kafkaConfigRepository;
    private final ActivityLogService activityLogService;
    private final SecretCipher secretCipher;
    private final KafkaConnection startupConnection;

    public KafkaConfigService(
            KafkaConfigRepository kafkaConfigRepository,
            ActivityLogService activityLogService,
            SecretCipher secretCipher,
            KafkaStartupProperties startup,
            @Value("${spring.kafka.bootstrap-servers}") String defaultBootstrapServers
    ) {
        this.kafkaConfigRepository = kafkaConfigRepository;
        this.activityLogService = activityLogService;
        this.secretCipher = secretCipher;
        this.startupConnection = new KafkaConnection(defaultBootstrapServers, startup.authentication(),
                startup.encrypted(), startup.username(), startup.password(), startup.caCertificate());
    }

    /**
     * Get the current bootstrap servers.
     * Returns DB config if it exists, otherwise falls back to application.properties default.
     */
    public String getBootstrapServers() {
        return kafkaConfigRepository.findFirstByOrderByIdAsc()
                .map(KafkaConfig::getBootstrapServers)
                .orElse(startupConnection.bootstrapServers());
    }

    /**
     * How to reach the cluster currently set in Settings.
     * Every Kafka client is built from this (see KafkaConnection).
     *
     * @throws IllegalStateException if the saved password can't be read (DLQ_SECRET_KEY changed)
     */
    public KafkaConnection getConnection() {
        return kafkaConfigRepository.findFirstByOrderByIdAsc()
                .map(config -> toConnection(config, true))
                .orElse(startupConnection);
    }

    /**
     * The current settings as the Settings page may see them (no password).
     */
    public KafkaConfigView describe() {
        Optional<KafkaConfig> saved = kafkaConfigRepository.findFirstByOrderByIdAsc();
        KafkaConnection connection = saved.map(config -> toConnection(config, false)).orElse(startupConnection);
        boolean passwordSet = saved.map(config -> config.getPasswordEncrypted() != null)
                .orElse(startupConnection.password() != null);

        return new KafkaConfigView(connection.bootstrapServers(), saved.isPresent(), connection.authentication(),
                connection.encrypted(), connection.username(), passwordSet, connection.caCertificate(),
                secretCipher.isAvailable());
    }

    /**
     * Get the saved config, or null if none exists yet.
     */
    public Optional<KafkaConfig> getConfig() {
        return kafkaConfigRepository.findFirstByOrderByIdAsc();
    }

    /**
     * Check if a config has been saved (i.e. not first-time setup).
     */
    public boolean isConfigured() {
        return kafkaConfigRepository.findFirstByOrderByIdAsc().isPresent();
    }

    /**
     * Save or update the Kafka configuration.
     *
     * @throws IllegalArgumentException if the request is incomplete (see resolve)
     * @throws IllegalStateException    if a password has to be stored but DLQ_SECRET_KEY is not set
     */
    public KafkaConfigView saveConfig(KafkaConfigRequest request) {
        KafkaConnection connection = resolve(request);
        log.info("Saving Kafka config: {}", connection);

        KafkaConnection previous = currentWithoutFailing();
        KafkaConfig config = kafkaConfigRepository.findFirstByOrderByIdAsc()
                .orElse(new KafkaConfig());

        config.setBootstrapServers(connection.bootstrapServers());
        config.setAuthentication(connection.authentication());
        config.setEncrypted(connection.encrypted());
        config.setUsername(connection.username());
        config.setPasswordEncrypted(connection.password() == null ? null : secretCipher.encrypt(connection.password()));
        config.setCaCertificate(connection.caCertificate());
        kafkaConfigRepository.save(config);

        // KafkaConnection's text form never contains the password
        activityLogService.record(ActivityAction.KAFKA_SETTINGS_CHANGED, "Kafka connection",
                previous + " -> " + connection);
        return describe();
    }

    /**
     * Test connectivity with the given settings (nothing is saved).
     * Returns a map with success/failure and details.
     */
    public Map<String, Object> testConnection(KafkaConfigRequest request) {
        Map<String, Object> result = new HashMap<>();

        KafkaConnection connection;
        try {
            connection = resolve(request);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }

        log.info("Testing Kafka connection to: {}", connection);

        try {
            Map<String, Object> adminProps = connection.clientProperties();
            adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            adminProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);

            try (AdminClient adminClient = AdminClient.create(adminProps)) {
                String clusterId = adminClient.describeCluster().clusterId().get(5, TimeUnit.SECONDS);
                int brokerCount = adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS).size();

                result.put("success", true);
                result.put("clusterId", clusterId);
                result.put("brokerCount", brokerCount);
                log.info("Connection test successful. Cluster: {}, Brokers: {}", clusterId, brokerCount);
            }

        } catch (Exception e) {
            log.error("Connection test failed for: {}", connection, e);
            result.put("success", false);
            result.put("error", "Failed to connect: " + explain(e));
        }

        return result;
    }

    /**
     * Turn a request from the Settings page into a complete connection.
     *
     * - Security fields left out (authentication is null): the current security settings are kept
     * - Password left out: the saved password is reused, but only for the same brokers and the
     *   same username. A saved password is never sent to an address it wasn't saved for -
     *   otherwise changing the address would be a way to make the app hand it over.
     *
     * @throws IllegalArgumentException if something required is missing
     */
    KafkaConnection resolve(KafkaConfigRequest request) {
        if (request.bootstrapServers() == null || request.bootstrapServers().isBlank()) {
            throw new IllegalArgumentException("bootstrapServers is required");
        }

        KafkaConnection current = currentWithoutFailing();
        KafkaConnection requested = request.authentication() == null
                ? new KafkaConnection(request.bootstrapServers(), current.authentication(), current.encrypted(),
                        current.username(), request.password(), current.caCertificate())
                : new KafkaConnection(request.bootstrapServers(), request.authentication(),
                        Boolean.TRUE.equals(request.encrypted()), request.username(), request.password(),
                        request.caCertificate());

        if (!requested.authentication().needsLogin()) {
            return requested;
        }

        String login = requested.authentication().saslMechanism() + " login";
        if (requested.username() == null) {
            throw new IllegalArgumentException("A username is required for " + login);
        }
        if (requested.password() != null) {
            return requested;
        }

        // No password typed: fall back to the saved one
        if (current.password() == null) {
            throw new IllegalArgumentException("A password is required for " + login);
        }
        if (!current.bootstrapServers().equals(requested.bootstrapServers())
                || !requested.username().equals(current.username())) {
            throw new IllegalArgumentException(
                    "Enter the Kafka password again: the saved one is only reused for the same brokers and username");
        }
        return new KafkaConnection(requested.bootstrapServers(), requested.authentication(), requested.encrypted(),
                requested.username(), current.password(), requested.caCertificate());
    }

    /**
     * The current connection, with no password if the saved one can't be read
     * (so Settings can still be opened and fixed after DLQ_SECRET_KEY changed).
     */
    private KafkaConnection currentWithoutFailing() {
        return kafkaConfigRepository.findFirstByOrderByIdAsc()
                .map(config -> toConnection(config, false))
                .orElse(startupConnection);
    }

    private KafkaConnection toConnection(KafkaConfig config, boolean failIfPasswordUnreadable) {
        String password = null;
        if (config.getPasswordEncrypted() != null) {
            try {
                password = secretCipher.decrypt(config.getPasswordEncrypted());
            } catch (IllegalStateException e) {
                if (failIfPasswordUnreadable) {
                    throw e;
                }
            }
        }
        return new KafkaConnection(config.getBootstrapServers(), config.getAuthentication(),
                Boolean.TRUE.equals(config.getEncrypted()), config.getUsername(), password,
                config.getCaCertificate());
    }

    /**
     * Kafka's own message for the failure (e.g. "Authentication failed: Invalid username or
     * password"), except for two cases where it doesn't tell an admin what to do:
     *
     * - Timeouts ("Timed out waiting for a node assignment"): a wrong address, and a cluster
     *   that expects a login or encryption we didn't use, both end that way
     * - An untrusted certificate ("unable to find valid certification path to requested
     *   target"): the brokers' certificate was signed by an authority Java doesn't know
     */
    private String explain(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof org.apache.kafka.common.errors.TimeoutException
                || cause instanceof java.util.concurrent.TimeoutException) {
            return "no answer from the brokers. Check the address, and that the login and encryption "
                    + "settings match what the cluster expects.";
        }
        if (cause instanceof CertPathBuilderException) {
            return "the brokers' certificate is not trusted. If the cluster uses a company-internal "
                    + "certificate authority, add its certificate.";
        }
        return cause.getMessage();
    }
}
