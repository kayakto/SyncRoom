package ru.syncroom.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate used for external API calls.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(10000); // 10 seconds
        
        return new RestTemplate(factory);
    }

    /**
     * Longer timeouts for local SD / inference-mock (txt2img can exceed 10s on CPU).
     */
    @Bean(name = "inferenceRestTemplate")
    public RestTemplate inferenceRestTemplate(
            @Value("${bots.inference.connect-timeout-ms:15000}") int connectTimeoutMs,
            @Value("${bots.inference.read-timeout-ms:120000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
