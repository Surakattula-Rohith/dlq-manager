package com.dlqmanager.config;

import com.dlqmanager.model.enums.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Accounts that can sign in, read from dlq.auth.users[n].username / password / role
 *
 * Passwords can be plain text or already encoded with their id prefix, e.g. {bcrypt}$2a$10$...
 */
@ConfigurationProperties(prefix = "dlq.auth")
public record AuthProperties(List<Account> users) {

    public AuthProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record Account(String username, String password, Role role) {
    }

    /**
     * True while any account still uses the demo password (the same as its username)
     */
    public boolean demoAccountsInUse() {
        return users.stream()
                .anyMatch(account -> account.username() != null && account.username().equals(account.password()));
    }
}
