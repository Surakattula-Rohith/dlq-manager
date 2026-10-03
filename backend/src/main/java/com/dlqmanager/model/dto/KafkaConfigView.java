package com.dlqmanager.model.dto;

import com.dlqmanager.model.enums.KafkaAuthentication;

/**
 * Kafka connection settings as shown on the Settings page
 *
 * The password is deliberately missing: the page only learns whether one is saved.
 *
 * @param bootstrapServers  Kafka brokers in use
 * @param configured        true once settings were saved from the Settings page
 *                          (false = still running on the startup defaults)
 * @param authentication    how the app signs in
 * @param encrypted         true if the connection uses TLS
 * @param username          login name, if any
 * @param passwordSet       true if a password is in use
 * @param caCertificate     company certificate authority (PEM), if any - a certificate is public
 * @param canStorePassword  false when DLQ_SECRET_KEY is not set, so a password can't be saved
 */
public record KafkaConfigView(String bootstrapServers,
                              boolean configured,
                              KafkaAuthentication authentication,
                              boolean encrypted,
                              String username,
                              boolean passwordSet,
                              String caCertificate,
                              boolean canStorePassword) {
}
