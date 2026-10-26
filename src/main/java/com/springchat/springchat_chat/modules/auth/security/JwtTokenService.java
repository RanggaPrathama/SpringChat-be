package com.springchat.springchat_chat.modules.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration accessTtl;

    public JwtTokenService(@Value("${springchat.security.jwt.secret}") String secret,
            @Value("${springchat.security.jwt.issuer:springchat}") String issuer,
            @Value("${springchat.security.jwt.access-ttl:PT15M}") Duration accessTtl) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.issuer = issuer;
        this.accessTtl = accessTtl;
    }

    public String issue(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(List.of("springchat-api"))
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
