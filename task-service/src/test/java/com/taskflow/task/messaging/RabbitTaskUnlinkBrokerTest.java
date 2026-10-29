package com.taskflow.task.messaging;

import com.taskflow.task.entity.PendingTaskUnlink;
import com.taskflow.task.repository.PendingTaskUnlinkRepository;
import com.taskflow.task.service.TaskLinkCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfSystemProperty(named = "taskflow.rabbitmq.integration", matches = "true")
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "taskflow.lifecycle-events.publisher-enabled=false",
        "management.tracing.enabled=false",
        "taskflow.task-link-cleanup.enabled=false",
        "spring.rabbitmq.host=localhost",
        "taskflow.messaging.lifecycle-queue=taskflow.test.task.lifecycle",
        "taskflow.messaging.exchange=taskflow.test.publisher.events",
        "taskflow.messaging.task-unlink-queue=taskflow.test.publisher.unlink",
        "spring.datasource.url=jdbc:h2:mem:rabbit-publisher-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class RabbitTaskUnlinkBrokerTest {
    private static final String EXCHANGE = "taskflow.test.publisher.events";
    private static final String QUEUE = "taskflow.test.publisher.unlink";
    private static final long RECEIVE_TIMEOUT_MILLISECONDS = 5000;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TaskUnlinkPublisher publisher;

    @Autowired
    private PendingTaskUnlinkRepository pendingUnlinks;

    @BeforeEach
    void prepareQueue() {
        rabbitAdmin.initialize();
        rabbitAdmin.purgeQueue(QUEUE);
        pendingUnlinks.deleteAll();
    }

    @Test
    void publishesTheOutboxAndOnlyThenRemovesThePendingEntry() {
        pendingUnlinks.save(new PendingTaskUnlink(42L));
        new TaskLinkCleanup(pendingUnlinks, publisher).publishPendingUnlinks();
        assertThat(pendingUnlinks.existsById(42L)).isFalse();
        Message published = rabbitTemplate.receive(QUEUE, RECEIVE_TIMEOUT_MILLISECONDS);
        assertThat(published).isNotNull();
        assertThat(published.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(rabbitTemplate.getMessageConverter().fromMessage(published))
                .isEqualTo(new TaskUnlinkRequested(42L));
    }

    @Test
    void preservesTheOutboxWhenTheBrokerReturnsAnUnroutableMessage() {
        pendingUnlinks.save(new PendingTaskUnlink(43L));
        TaskUnlinkPublisher missingRoute = new RabbitTaskUnlinkPublisher(rabbitTemplate, EXCHANGE, "missing.route");
        new TaskLinkCleanup(pendingUnlinks, missingRoute).publishPendingUnlinks();
        assertThat(pendingUnlinks.existsById(43L)).isTrue();
        assertThatThrownBy(() -> missingRoute.publish(43L))
                .isInstanceOf(TaskUnlinkPublicationException.class);
    }
}
