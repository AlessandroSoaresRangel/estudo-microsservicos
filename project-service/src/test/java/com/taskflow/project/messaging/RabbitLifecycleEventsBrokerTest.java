package com.taskflow.project.messaging;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.project.repository.LifecycleOutboxRepository;
import com.taskflow.project.dto.ProjectRequest;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "taskflow.rabbitmq.integration", matches = "true")
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false",
        "taskflow.task-link-cleanup.enabled=false",
        "taskflow.lifecycle-events.publisher-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.host=localhost",
        "taskflow.messaging.exchange=taskflow.test.project-lifecycle.events",
        "taskflow.messaging.task-unlink-queue=taskflow.test.project-lifecycle.unlink",
        "taskflow.messaging.lifecycle-queue=taskflow.test.project-lifecycle.queue",
        "spring.datasource.url=jdbc:h2:mem:project-lifecycle-broker-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class RabbitLifecycleEventsBrokerTest {
    private static final String EXCHANGE = "taskflow.test.project-lifecycle.events";
    private static final String QUEUE = "taskflow.test.project-lifecycle.queue";
    private static final long RECEIVE_TIMEOUT_MILLISECONDS = 5000;

    @Autowired
    private LifecycleOutboxRepository outbox;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectService service;

    @Autowired
    private ProjectRepository repository;

    @BeforeEach
    void prepare() {
        rabbitAdmin.initialize();
        rabbitAdmin.purgeQueue(QUEUE);
        outbox.deleteAll();
        repository.deleteAll();
    }

    @Test
    void deliversCreatedUpdatedAndDeletedEventsWithPersistentSnapshots() throws Exception {
        var created = service.create(new ProjectRequest("Evento inicial", "Descrição original", null));
        service.update(created.id(), new ProjectRequest("Evento atualizado", "Descrição atualizada", null));
        service.delete(created.id());
        assertThat(outbox.count()).isEqualTo(3);
        new LifecycleOutboxPublisher(outbox, rabbitTemplate, EXCHANGE).publishPendingEvents();
        assertThat(outbox.count()).isZero();
        List<String> receivedTypes = new ArrayList<>();
        List<String> receivedNames = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Message message = rabbitTemplate.receive(QUEUE, RECEIVE_TIMEOUT_MILLISECONDS);
            assertThat(message).isNotNull();
            var event = objectMapper.readTree(message.getBody());
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.get("eventId").asText());
            assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(java.time.Instant.parse(event.get("occurredAt").asText())).isNotNull();
            assertThat(event.get("data").get("projectId").asLong()).isEqualTo(created.id());
            receivedTypes.add(event.get("eventType").asText());
            receivedNames.add(event.get("data").get("name").asText());
        }
        assertThat(receivedTypes).containsExactly("project.created.v1", "project.updated.v1", "project.deleted.v1");
        assertThat(receivedNames).containsExactly("Evento inicial", "Evento atualizado", "Evento atualizado");
    }

    @Test
    void preservesTheEventIdAndPayloadWhenRetryingAnUnroutableEvent() {
service.create(new ProjectRequest("Evento recuperado", null, null));
        var pending = outbox.findAll().getFirst();
        Binding binding = new Binding(QUEUE, Binding.DestinationType.QUEUE, EXCHANGE, "project.created.v1", null);
        rabbitAdmin.removeBinding(binding);
        LifecycleOutboxPublisher publisher = new LifecycleOutboxPublisher(outbox, rabbitTemplate, EXCHANGE);
        try {
            publisher.publishPendingEvents();
            assertThat(outbox.count()).isEqualTo(1);
        } finally {
            rabbitAdmin.declareBinding(binding);
        }
        publisher.publishPendingEvents();
        assertThat(outbox.count()).isZero();
        Message delivered = rabbitTemplate.receive(QUEUE, RECEIVE_TIMEOUT_MILLISECONDS);
        assertThat(delivered).isNotNull();
        assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(pending.getEventId().toString());
        assertThat(new String(delivered.getBody(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(pending.getPayload());
    }
}
