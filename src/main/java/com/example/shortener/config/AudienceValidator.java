package com.example.shortener.config;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // check aud from jwt, and whether it contains expected audience
        List<String> audiences = jwt.getAudience();
        if (audiences == null || audiences.isEmpty()) {
            OAuth2Error error = new OAuth2Error("invalid_token", "The token does not contain an audience claim.", null);
            return OAuth2TokenValidatorResult.failure(error);
        } else if (!jwt.getAudience().contains(expectedAudience)) {
            OAuth2Error error = new OAuth2Error("invalid_token", "The token audience does not match the expected audience.", null);
            return OAuth2TokenValidatorResult.failure(error);
        } else {
            return OAuth2TokenValidatorResult.success();
        }
    }
    
}
