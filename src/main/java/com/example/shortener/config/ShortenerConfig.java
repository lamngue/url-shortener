package com.example.shortener.config;

import com.example.shortener.cache.LinkCacheProperties;
import com.example.shortener.shortcode.ShortCodeGenerator;

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
