package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.KafkaAuthentication;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity storing Kafka connection configuration.
 * Only one row is expected (singleton config).
 */
@Entity
@Table(name = "kafka_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bootstrap_servers", nullable = false)
    private String bootstrapServers;

    /**
     * How the app signs in to Kafka. Null (rows saved before this existed) means no login.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "authentication")
    private KafkaAuthentication authentication;

    /**
     * True to connect over TLS. Null means not encrypted.
     */
    @Column(name = "encrypted")
    private Boolean encrypted;

    @Column(name = "username")
    private String username;

    /**
     * The Kafka password, encrypted by SecretCipher. Never stored or logged as plain text.
     */
    @ToString.Exclude
    @Column(name = "password_encrypted", columnDefinition = "TEXT")
    private String passwordEncrypted;

    /**
     * Company certificate authority (PEM text), when the brokers use an internal one
     */
    @ToString.Exclude
    @Column(name = "ca_certificate", columnDefinition = "TEXT")
    private String caCertificate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
