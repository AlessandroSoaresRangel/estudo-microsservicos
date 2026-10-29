package com.taskflow.task;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        properties = {
                "eureka.client.enabled=false",
                "taskflow.task-link-cleanup.enabled=false",
                "management.zipkin.tracing.export.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:task-service-test",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver"
        }
)
class PersistenceConfigurationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void configuresJpaAndConnectsToTheConfiguredDatabase() throws Exception {
        assertThat(entityManagerFactory).isNotNull();
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }

    @Test
    void rejectsANonEmptySchemaWithoutMigrationHistory() throws Exception {
        String databaseUrl = "jdbc:h2:mem:baseline-rejection-test;DB_CLOSE_DELAY=-1";
        Flyway databaseMigration = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .dataSource(databaseUrl, "sa", "")
                .load();

        try (var connection = databaseMigration.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE existing_data (id BIGINT PRIMARY KEY)");
        }

        assertThatThrownBy(databaseMigration::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema")
                .hasMessageContaining("no schema history table");
    }

    @Test
    void addsDescriptionWhilePreservingExistingTasks() {
        String databaseUrl = "jdbc:h2:mem:description-migration-test;DB_CLOSE_DELAY=-1";
        Flyway initialMigration = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .dataSource(databaseUrl, "sa", "")
                .target("1")
                .load();
        initialMigration.migrate();
        JdbcTemplate existingDatabase = new JdbcTemplate(initialMigration.getConfiguration().getDataSource());
        existingDatabase.update("INSERT INTO tasks (title) VALUES (?)", "Tarefa existente");

        Flyway.configure()
                .configuration(flyway.getConfiguration())
                .dataSource(databaseUrl, "sa", "")
                .load()
                .migrate();

        assertThat(existingDatabase.queryForObject("SELECT title FROM tasks", String.class))
                .isEqualTo("Tarefa existente");
        assertThat(existingDatabase.queryForObject("SELECT description FROM tasks", String.class))
                .isNull();
        existingDatabase.update("UPDATE tasks SET description = ?", "Descrição persistida");
        assertThat(existingDatabase.queryForObject("SELECT description FROM tasks", String.class))
                .isEqualTo("Descrição persistida");
    }

    @Test
    void appliesTheInitialFlywayMigration() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'tasks'",
                Integer.class
        )).isEqualTo(1);
    }
}
