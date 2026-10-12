package com.example.shortener.config;

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

    @Bean
    public OpenAPI customOpenAPI(JwtProperties jwtProperties) {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(jwtProperties.issuer() + "authorize") 
                                                .tokenUrl(jwtProperties.issuer() + "oauth/token")
                                                .scopes(new Scopes()
                                                .addString("openid", "Sign in with OpenID Connect")
                                                .addString("profile", "Your name")
                                                .addString("email", "Your email address"))
                                        ))
                                .description("OAuth2 authentication with Auth0")
                ))
                .info(new Info().title("URL Shortener API").version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
