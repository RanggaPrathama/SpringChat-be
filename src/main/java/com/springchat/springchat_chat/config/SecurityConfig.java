package com.springchat.springchat_chat.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers("/api/v1/users/**", "/api/v1/conversations/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/api/v1/auth/**", "/ws", "/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${springchat.security.jwt.secret}") String secret,
            @Value("${springchat.security.jwt.issuer:springchat}") String issuer) {
        SecretKey key = secretKey(secret);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<java.util.List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains("springchat-api"))));
        return decoder;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/api/v1");
        return repository;
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> writeError(response,
                HttpServletResponse.SC_FORBIDDEN, "Access denied", "FORBIDDEN");
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> writeError(response,
                HttpServletResponse.SC_UNAUTHORIZED, "Authentication required", "UNAUTHORIZED");
    }

    private static SecretKey secretKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static void writeError(HttpServletResponse response, int status, String message, String code)
            throws java.io.IOException {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"" + message
                + "\",\"error\":\"" + code + "\"}");
    }
}
