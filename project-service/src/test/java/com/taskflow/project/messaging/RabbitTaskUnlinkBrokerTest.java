package com.taskflow.project.messaging;

import java.util.Set;

import com.taskflow.project.entity.Project;
import com.taskflow.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnabledIfSystemProperty(named = "taskflow.rabbitmq.integration", matches = "true")
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "taskflow.lifecycle-events.publisher-enabled=false",
        "management.tracing.enabled=false",
        "spring.rabbitmq.host=localhost",
        "taskflow.messaging.lifecycle-queue=taskflow.test.project.lifecycle",
        "taskflow.messaging.exchange=taskflow.test.consumer.events",
        "taskflow.messaging.task-unlink-queue=taskflow.test.consumer.unlink",
        "spring.datasource.url=jdbc:h2:mem:rabbit-consumer-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class RabbitTaskUnlinkBrokerTest {
    private static final String EXCHANGE = "taskflow.test.consumer.events";
    private static final String QUEUE = "taskflow.test.consumer.unlink";
    private static final String ROUTING_KEY = "task.unlink-requested.v1";
    private static final long DEAD_LETTER_TIMEOUT_MILLISECONDS = 10000;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.taskflow.project.repository.LifecycleOutboxRepository lifecycleOutbox;

    @BeforeEach
    void clearQueuesAndProjects() {
        rabbitAdmin.initialize();
        rabbitAdmin.purgeQueue(QUEUE);
        rabbitAdmin.purgeQueue(QUEUE + ".dlq");
        lifecycleOutbox.deleteAll();
        projectRepository.deleteAll();
    }

    @Test
    void consumesDuplicateMessagesWithoutDeletingTheProject() {
        Project project = projectRepository.saveAndFlush(new Project("Projeto RabbitMQ", null, Set.of(42L)));
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, new TaskUnlinkRequested(42L));
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, new TaskUnlinkRequested(42L));
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM project_tasks", Long.class)).isZero();
            assertThat(projectRepository.existsById(project.getId())).isTrue();
            assertThat(lifecycleOutbox.findAll())
                    .extracting(com.taskflow.project.entity.LifecycleOutboxEvent::getEventType)
                    .containsExactly("project.updated.v1");
        });
    }

    @Test
    void sendsInvalidMessagesToTheDeadLetterQueue() {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, new TaskUnlinkRequested(-1L));
        assertThat(rabbitTemplate.receiveAndConvert(QUEUE + ".dlq", DEAD_LETTER_TIMEOUT_MILLISECONDS))
                .isEqualTo(new TaskUnlinkRequested(-1L));
    }
}
