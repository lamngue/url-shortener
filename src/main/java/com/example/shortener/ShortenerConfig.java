package com.example.shortener;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ShortenerProperties.class, LinkCacheProperties.class})
public class ShortenerConfig {

    @Bean
    ShortCodeGenerator shortCodeGenerator(ShortenerProperties properties) {
        return new ShortCodeGenerator(properties.shortCodeK());
    }
}
