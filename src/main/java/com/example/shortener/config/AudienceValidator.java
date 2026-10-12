package com.example.shortener.config;

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
        // Implement the logic to validate the audience of the token
        // For example, decode the token and check if the audience matches expectedAudience
        if (jwt.getAudience().contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        } else {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The required audience is missing", null));
        }
    }
    
}
