package com.dlqmanager.model.dto;

import com.dlqmanager.model.enums.KafkaAuthentication;

/**
 * Kafka connection settings sent from the Settings page (to test or to save)
 *
 * @param bootstrapServers Kafka brokers (required)
 * @param authentication   NONE, PLAIN, SCRAM_SHA_256 or SCRAM_SHA_512.
 *                         Left out = keep the security settings that are already saved.
 * @param encrypted        true to connect over TLS
 * @param username         login name
 * @param password         login password. Left out = keep the saved one (only for the
 *                         same brokers and username). It is never sent back to the browser.
 * @param caCertificate    company certificate authority as PEM text (optional)
 */
public record KafkaConfigRequest(String bootstrapServers,
                                 KafkaAuthentication authentication,
                                 Boolean encrypted,
                                 String username,
                                 String password,
                                 String caCertificate) {
}
