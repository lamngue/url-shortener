package com.example.shortener.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;


@Configuration 
public class OpenApiConfig {
    private static final String SECURITY_SCHEME = "OAuth2";
    private JwtProperties jwtProperties = null;

    @Value(${"springdoc.swagger-ui.oauth.scopes"})
    String scopes;

    SecurityScheme securityScheme = new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(jwtProperties.issuer() + "authorize") 
                                                .tokenUrl(jwtProperties.issuer() + "oauth/token")
                                                .scopes(new Scopes())
                                        ))
                                .description("OAuth2 authentication with Auth0");

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, securityScheme))
                .info(new Info().title("URL Shortener API").version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
