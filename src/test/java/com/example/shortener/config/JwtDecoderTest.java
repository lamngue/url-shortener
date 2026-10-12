package com.example.shortener.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The checks wired in SecurityConfig, with real signed tokens: signature, pinned algorithm,
 * exp and iss. (The aud check gets its own tests once it exists.)
 */
class JwtDecoderTest {

    private final JwtDecoder decoder = TestJwts.decoder();

    @Test
    void validTokenIsAcceptedAndCarriesTheSubject() {
        Jwt jwt = decoder.decode(TestJwts.rs256(claims -> { }));

        assertThat(jwt.getSubject()).isEqualTo("alice");
    }

    @Test
    void expiredTokenIsRejected() {
        // Two minutes ago: past Spring's default 60 s clock skew.
        String token = TestJwts.rs256(claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(120))));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = TestJwts.rs256(claims -> claims.issuer("https://evil.test"));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnUntrustedKeyIsRejected() {
        String token = TestJwts.rs256WithUntrustedKey(claims -> { });

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithAnotherAlgorithmIsRejected() {
        String token = TestJwts.hs256(claims -> { });

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        String token = TestJwts.unsigned(claims -> { });

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
