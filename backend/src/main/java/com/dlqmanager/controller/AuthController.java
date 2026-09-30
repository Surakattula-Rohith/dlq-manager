package com.dlqmanager.controller;

import com.dlqmanager.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sign-in status for the web UI
 *
 * Login and logout themselves are handled by Spring Security (see SecurityConfig):
 * - POST /api/auth/login   form fields "username" and "password"
 * - POST /api/auth/logout
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthProperties authProperties;

    /**
     * GET /api/auth/me - Who is signed in?
     *
     * Always returns 200, so the UI can tell "not signed in" apart from a real error.
     */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return sessionInfo(authentication, authProperties.demoAccountsInUse());
    }

    public static Map<String, Object> sessionInfo(Authentication authentication, boolean demoAccounts) {
        boolean signedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("authenticated", signedIn);
        if (signedIn) {
            info.put("username", authentication.getName());
            info.put("role", roleOf(authentication));
        }
        info.put("demoAccounts", demoAccounts);
        return info;
    }

    private static String roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }
}
