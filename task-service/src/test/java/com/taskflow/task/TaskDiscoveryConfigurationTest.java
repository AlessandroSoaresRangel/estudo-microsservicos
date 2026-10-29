package com.taskflow.task;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDiscoveryConfigurationTest {
    @Test
    void fetchesTheRegistryToDiscoverTheProjectService() throws Exception {
        Properties configuration = new Properties();
        try (var resource = getClass().getResourceAsStream("/application.properties")) {
            configuration.load(resource);
        }
        assertThat(configuration.getProperty("eureka.client.fetch-registry")).isEqualTo("true");
    }
}
