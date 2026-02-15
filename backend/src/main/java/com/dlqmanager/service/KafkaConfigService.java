package com.dlqmanager.service;

import com.dlqmanager.model.entity.KafkaConfig;
import com.dlqmanager.repository.KafkaConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class KafkaConfigService {

    private final KafkaConfigRepository kafkaConfigRepository;
    private final String defaultBootstrapServers;

    public KafkaConfigService(
            KafkaConfigRepository kafkaConfigRepository,
            @Value("${spring.kafka.bootstrap-servers}") String defaultBootstrapServers
    ) {
        this.kafkaConfigRepository = kafkaConfigRepository;
        this.defaultBootstrapServers = defaultBootstrapServers;
    }

    /**
     * Get the current bootstrap servers.
     * Returns DB config if it exists, otherwise falls back to application.properties default.
     */
    public String getBootstrapServers() {
        return kafkaConfigRepository.findFirstByOrderByIdAsc()
                .map(KafkaConfig::getBootstrapServers)
                .orElse(defaultBootstrapServers);
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
     */
    public KafkaConfig saveConfig(String bootstrapServers) {
        log.info("Saving Kafka config: bootstrapServers={}", bootstrapServers);

        KafkaConfig config = kafkaConfigRepository.findFirstByOrderByIdAsc()
                .orElse(new KafkaConfig());

        config.setBootstrapServers(bootstrapServers.trim());
        return kafkaConfigRepository.save(config);
    }

    /**
     * Test connectivity to the given bootstrap servers.
     * Returns a map with success/failure and details.
     */
    public Map<String, Object> testConnection(String bootstrapServers) {
        log.info("Testing Kafka connection to: {}", bootstrapServers);

        Map<String, Object> result = new HashMap<>();

        Map<String, Object> adminProps = new HashMap<>();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers.trim());
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        adminProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);

        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            String clusterId = adminClient.describeCluster().clusterId().get(5, TimeUnit.SECONDS);
            int brokerCount = adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS).size();

            result.put("success", true);
            result.put("clusterId", clusterId);
            result.put("brokerCount", brokerCount);
            log.info("Connection test successful. Cluster: {}, Brokers: {}", clusterId, brokerCount);

        } catch (Exception e) {
            log.error("Connection test failed for: {}", bootstrapServers, e);
            result.put("success", false);
            result.put("error", "Failed to connect: " + getRootCauseMessage(e));
        }

        return result;
    }

    private String getRootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
