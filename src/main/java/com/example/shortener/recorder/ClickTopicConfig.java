package com.example.shortener.recorder;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class ClickTopicConfig {

    public static final String TOPIC = "link-clicks";

    // Created at startup if it does not exist. The code is the message key, so all clicks for
    // one link go to the same partition.
    @Bean
    NewTopic linkClicksTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }
}
