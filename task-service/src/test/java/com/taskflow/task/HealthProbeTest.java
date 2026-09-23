package com.taskflow.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false", "management.zipkin.tracing.export.enabled=false", "spring.datasource.url=jdbc:h2:mem:health-probe-test", "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver"})
class HealthProbeTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exposesLivenessAndReadinessProbes() {
        for (var path : new String[]{
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/livez",
                "/readyz"
        }) {
            ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

            assertThat(response.getStatusCode().value()).as(path).isEqualTo(200);
            assertThat(response.getBody()).as(path).contains("\"status\":\"UP\"");
        }
    }

    @Test
    void exposesMetricsAndPrometheusEndpoints() {
        ResponseEntity<String> metrics = restTemplate.getForEntity("/actuator/metrics", String.class);
        ResponseEntity<String> prometheus = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode().value()).isEqualTo(200);
        assertThat(metrics.getBody()).contains("names");
        assertThat(prometheus.getStatusCode().value()).isEqualTo(200);
        assertThat(prometheus.getBody())
                .contains("jvm_memory_used_bytes")
                .contains("http_server_requests_seconds_bucket");
    }
}
