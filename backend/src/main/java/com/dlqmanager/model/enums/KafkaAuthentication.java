package com.dlqmanager.model.enums;

/**
 * How the app signs in to the Kafka cluster
 *
 * NONE          - no login; anyone who can reach the brokers can connect (local development)
 * PLAIN         - username and password, sent as they are. Only safe on an encrypted
 *                 connection. Used by Confluent Cloud (API key + secret).
 * SCRAM_SHA_256 - username and password, but the password itself never goes over the network.
 * SCRAM_SHA_512   Used by AWS MSK, Aiven, Redpanda and most self-hosted clusters.
 */
public enum KafkaAuthentication {

    NONE(null, null),
    PLAIN("PLAIN", "org.apache.kafka.common.security.plain.PlainLoginModule"),
    SCRAM_SHA_256("SCRAM-SHA-256", "org.apache.kafka.common.security.scram.ScramLoginModule"),
    SCRAM_SHA_512("SCRAM-SHA-512", "org.apache.kafka.common.security.scram.ScramLoginModule");

    private final String saslMechanism;
    private final String loginModule;

    KafkaAuthentication(String saslMechanism, String loginModule) {
        this.saslMechanism = saslMechanism;
        this.loginModule = loginModule;
    }

    /**
     * Value for Kafka's sasl.mechanism setting (null for NONE)
     */
    public String saslMechanism() {
        return saslMechanism;
    }

    /**
     * Kafka class that performs the login (null for NONE)
     */
    public String loginModule() {
        return loginModule;
    }

    public boolean needsLogin() {
        return this != NONE;
    }
}
