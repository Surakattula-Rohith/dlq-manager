package com.dlqmanager.config;

import com.dlqmanager.model.enums.KafkaAuthentication;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka security settings given at startup (application.properties / environment variables)
 *
 * Used together with spring.kafka.bootstrap-servers until connection settings are saved
 * from the Settings page. A deployment can keep the Kafka password in its own secret store
 * this way, so it never has to be typed into the app or stored in its database.
 *
 * @param authentication NONE, PLAIN, SCRAM_SHA_256 or SCRAM_SHA_512
 * @param encrypted      true to connect over TLS
 * @param username       login name
 * @param password       login password
 * @param caCertificate  company certificate authority as PEM text
 */
@ConfigurationProperties(prefix = "dlq.kafka")
public record KafkaStartupProperties(KafkaAuthentication authentication,
                                     boolean encrypted,
                                     String username,
                                     String password,
                                     String caCertificate) {
}
