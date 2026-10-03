package com.dlqmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets before they are stored in the database
 *
 * The Kafka password typed into Settings has to be kept somewhere, and the app must be able
 * to read it back to connect (so it can't be hashed like a login password). It is stored
 * encrypted with AES-256-GCM, using a key derived from DLQ_SECRET_KEY.
 *
 * That key is given to the app at startup and is never written to the database. Someone
 * who gets hold of a database dump or backup therefore can't read the Kafka password.
 *
 * Without DLQ_SECRET_KEY nothing can be stored: saving a Kafka password is refused with a
 * clear message instead of quietly storing it as plain text.
 */
@Component
@Slf4j
public class SecretCipher {

    /**
     * Marks a stored value as encrypted, and with which scheme (room to change it later)
     */
    private static final String PREFIX = "enc:v1:";

    /**
     * Salt for turning DLQ_SECRET_KEY into an AES key (hex). It doesn't need to be secret.
     */
    private static final String KEY_SALT = "646c712d6d616e61676572";

    private final TextEncryptor encryptor;

    public SecretCipher(@Value("${dlq.secret-key:}") String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            this.encryptor = null;
            log.info("DLQ_SECRET_KEY is not set: a Kafka password can't be saved from the Settings page");
        } else {
            // AES-256 in GCM mode, a new random IV for every value
            this.encryptor = Encryptors.delux(secretKey, KEY_SALT);
        }
    }

    /**
     * @return true if a secret key was given, so secrets can be stored
     */
    public boolean isAvailable() {
        return encryptor != null;
    }

    /**
     * @throws IllegalStateException if no secret key was given
     */
    public String encrypt(String secret) {
        if (encryptor == null) {
            throw new IllegalStateException(
                    "A Kafka password can't be saved until DLQ_SECRET_KEY is set on the server (it encrypts the password)");
        }
        return PREFIX + encryptor.encrypt(secret);
    }

    /**
     * @throws IllegalStateException if the value can't be read with the current secret key
     */
    public String decrypt(String stored) {
        if (encryptor == null || stored == null || !stored.startsWith(PREFIX)) {
            throw unreadable(null);
        }
        try {
            return encryptor.decrypt(stored.substring(PREFIX.length()));
        } catch (RuntimeException e) {
            throw unreadable(e);
        }
    }

    private static IllegalStateException unreadable(Exception cause) {
        return new IllegalStateException(
                "The saved Kafka password can't be read with the current DLQ_SECRET_KEY. "
                        + "Enter the password again in Settings.", cause);
    }
}
