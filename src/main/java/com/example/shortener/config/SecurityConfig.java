package com.example.shortener.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API is an OAuth2 resource server (D-05): /api needs a valid access token; the redirect,
 * health and the API docs stay public.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**").authenticated()
                        // GET /{code}, /actuator/health, /swagger-ui/**, /v3/api-docs
                        .anyRequest().permitAll())
                // Missing or invalid token -> 401 with a WWW-Authenticate header.
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                // Every request carries its own token: no session, and no cookie for CSRF to abuse.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    // Keys are fetched from the JWKS URI on the first token and cached. Only RS256 is accepted,
    // so a token signed with another algorithm (or "none") is rejected before any claim is read.
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(tokenValidator(properties));
        return decoder;
    }

    /**
     * Checks after the signature: exp and nbf (with Spring's default 60 s clock skew) and iss.
     * The aud check is still to be added here.
     */
    public static OAuth2TokenValidator<Jwt> tokenValidator(JwtProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), new AudienceValidator(properties.audience()));
    }
}
