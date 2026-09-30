package com.dlqmanager.config;

import com.dlqmanager.controller.AuthController;
import com.dlqmanager.model.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Security setup
 *
 * - Every /api endpoint needs a signed-in user, except /api/auth/** (login, logout, "who am I")
 * - The web UI signs in once (POST /api/auth/login) and then uses the session cookie
 * - Scripts can send a username and password with every request instead (HTTP Basic)
 * - Session cookies are protected against CSRF: the UI reads the XSRF-TOKEN cookie and sends it
 *   back in the X-XSRF-TOKEN header (axios does this automatically)
 * - Calls without a valid user get 401 - no redirect and no browser login popup
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@Slf4j
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                   AuthProperties authProperties) throws Exception {
        HttpStatusEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);

        http
                .authorizeHttpRequests(auth -> auth
                        // Streamed downloads finish in an async dispatch and errors are rendered in an
                        // error dispatch - both belong to a request that was already checked
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> {
                            // Login replaces the CSRF token - load the new one so its cookie goes out with this response
                            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                            if (csrfToken != null) {
                                csrfToken.getToken();
                            }
                            writeJson(response, objectMapper,
                                    AuthController.sessionInfo(authentication, authProperties.demoAccountsInUse()));
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            writeJson(response, objectMapper, Map.of("error", "Wrong username or password"));
                        }))
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorized))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // A request that carries its own credentials (a script using HTTP Basic)
                        // can't be forged by another website, so it doesn't need a CSRF token
                        .ignoringRequestMatchers(request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(AuthProperties authProperties, PasswordEncoder passwordEncoder) {
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();

        for (AuthProperties.Account account : authProperties.users()) {
            if (!StringUtils.hasText(account.username()) || !StringUtils.hasText(account.password())) {
                throw new IllegalStateException("Every dlq.auth.users entry needs a username and a password");
            }
            Role role = account.role() != null ? account.role() : Role.VIEWER;
            users.createUser(User.withUsername(account.username())
                    .password(encode(account.password(), passwordEncoder))
                    .roles(role.name())
                    .build());
        }

        if (authProperties.users().isEmpty()) {
            log.warn("No login accounts configured (dlq.auth.users) - nobody can sign in");
        } else if (authProperties.demoAccountsInUse()) {
            log.warn("Demo accounts are active (password = username). Set DLQ_ADMIN_PASSWORD, "
                    + "DLQ_OPERATOR_PASSWORD and DLQ_VIEWER_PASSWORD before sharing the app.");
        }

        return users;
    }

    /**
     * Plain-text passwords from the config are hashed once at startup, so only hashes stay in memory.
     * Values that are already encoded ("{bcrypt}...") are used as they are.
     */
    private static String encode(String password, PasswordEncoder passwordEncoder) {
        boolean alreadyEncoded = password.startsWith("{") && password.indexOf('}') > 1;
        return alreadyEncoded ? password : passwordEncoder.encode(password);
    }

    private static void writeJson(HttpServletResponse response, ObjectMapper objectMapper, Object body)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * Accepts the raw token from the X-XSRF-TOKEN header (what a single-page app sends),
     * and keeps the BREACH-safe encoded token for everything else
     */
    private static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
                return super.resolveCsrfTokenValue(request, csrfToken);
            }
            return xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /**
     * The CSRF token is created lazily. Loading it on every request makes sure the browser
     * gets the XSRF-TOKEN cookie before its first POST.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
