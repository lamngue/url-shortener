package com.example.shortener.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class AudienceValidatorTest {
    private final AudienceValidator validator = new AudienceValidator("url-shortener");

    @Test
    void testAudienceValidationSuccess() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("aud", "url-shortener")
                .build();
        assertFalse(validator.validate(jwt).hasErrors(), "Expected audience to be valid");
    }

    @Test
    void testAudienceValidationFailure() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("aud", "other-api")
                .build();
        assertTrue(validator.validate(jwt).hasErrors(), "Expected audience to be invalid");
    }

    @Test
    void testNoAudienceClaim() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("alice").build();
        assertTrue(validator.validate(jwt).hasErrors(), "Expected audience to be invalid");
    }

    @Test 
    void testNoPartialAudienceMatch() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("aud", "url-shortener-extra")
                .build();
        assertTrue(validator.validate(jwt).hasErrors(), "Expected audience to be fully matched, not partially matched");
    }

}
