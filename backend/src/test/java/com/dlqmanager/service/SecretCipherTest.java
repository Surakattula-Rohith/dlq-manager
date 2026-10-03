package com.dlqmanager.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("a-long-random-key");

    @Test
    void whatIsStoredIsNotThePasswordButCanBeReadBack() {
        String stored = cipher.encrypt("kafka-pa$$word");

        assertThat(stored).startsWith("enc:v1:").doesNotContain("kafka-pa$$word");
        assertThat(cipher.decrypt(stored)).isEqualTo("kafka-pa$$word");
    }

    @Test
    void theSamePasswordIsStoredDifferentlyEveryTime() {
        assertThat(cipher.encrypt("kafka-password")).isNotEqualTo(cipher.encrypt("kafka-password"));
    }

    @Test
    void anotherKeyCannotReadIt() {
        String stored = cipher.encrypt("kafka-password");
        SecretCipher otherKey = new SecretCipher("a-different-key");

        assertThatThrownBy(() -> otherKey.decrypt(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Enter the password again");
    }

    @Test
    void aTamperedValueIsRejected() {
        String stored = cipher.encrypt("kafka-password");
        char last = stored.charAt(stored.length() - 1);
        String tampered = stored.substring(0, stored.length() - 1) + (last == '0' ? '1' : '0');

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutASecretKeyNothingIsStored() {
        SecretCipher noKey = new SecretCipher("");

        assertThat(noKey.isAvailable()).isFalse();
        assertThatThrownBy(() -> noKey.encrypt("kafka-password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DLQ_SECRET_KEY");
    }
}
