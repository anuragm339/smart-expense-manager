package com.flying.solo.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate configuration for Azure OpenAI API calls
 * Handles o1-mini model's long processing time (2-5 minutes)
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Custom RestTemplate for OpenAI API with extended timeouts
     * - Connect timeout: 30 seconds (time to establish connection)
     * - Read timeout: 6 minutes (time to wait for o1-mini response)
     *
     * Usage: Inject with @Qualifier("openAIRestTemplate")
     */
    @Bean(name = "openAIRestTemplate")
    public RestTemplate openAIRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(30))     // Connection timeout: 30 seconds
                .setReadTimeout(Duration.ofMinutes(6))         // Read timeout: 6 minutes (for o1-mini)
                .build();
    }

    /**
     * Alternative configuration using SimpleClientHttpRequestFactory
     * Use this if you need millisecond-level control
     */
    @Bean(name = "openAIRestTemplateAdvanced")
    public RestTemplate openAIRestTemplateAdvanced() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Connection timeout: How long to wait to establish TCP connection (30 sec)
        factory.setConnectTimeout(30000);  // milliseconds

        // Read timeout: How long to wait for response after connection (6 min for o1-mini)
        factory.setReadTimeout(360000);    // 6 minutes = 360,000 ms

        return new RestTemplate(factory);
    }
}
