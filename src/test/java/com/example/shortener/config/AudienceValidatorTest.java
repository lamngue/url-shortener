package com.example.shortener.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class AudienceValidatorTest {
    private final AudienceValidator validator = new AudienceValidator("url-shortener");

    @ParameterizedTest 
    @ValueSource(strings = {"url-shortener", "other-audience", "another-audience"})
    void testAudienceValidationSuccess(String audience) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("aud", audience)
                .build();
        if ("url-shortener".equals(audience)) {
            assertTrue(validator.validate(jwt).hasErrors() == false, "Expected audience to be valid");
        } else {
            assertTrue(validator.validate(jwt).hasErrors(), "Expected audience to be invalid");
        }       
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
