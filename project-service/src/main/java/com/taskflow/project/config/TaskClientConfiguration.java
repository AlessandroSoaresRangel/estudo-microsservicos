package com.taskflow.project.config;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TaskClientConfiguration {
    private static final int CONNECT_TIMEOUT_MILLISECONDS = 1000;
    private static final int READ_TIMEOUT_MILLISECONDS = 2000;

    @Bean
    @LoadBalanced
    public RestClient.Builder taskRestClientBuilder(RestClientBuilderConfigurer configurer) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLISECONDS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLISECONDS);
        return configurer.configure(RestClient.builder()).requestFactory(requestFactory);
    }
}
