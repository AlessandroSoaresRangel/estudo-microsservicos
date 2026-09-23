package com.taskflow.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "eureka.client.enabled=false",
                "management.zipkin.tracing.export.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:task-service-test",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver"
        }
)
class PersistenceConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void configuresJpaAndConnectsToTheConfiguredDatabase() throws Exception {
        assertThat(entityManagerFactory).isNotNull();
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }
}
