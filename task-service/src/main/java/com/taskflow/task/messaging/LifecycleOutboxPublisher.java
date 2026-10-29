package com.taskflow.task.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.taskflow.task.entity.LifecycleOutboxEvent;
import com.taskflow.task.repository.LifecycleOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Entrega ao broker o JSON já persistido, preservando ID e conteúdo entre tentativas. */
@Component
@ConditionalOnProperty(name = "taskflow.lifecycle-events.publisher-enabled", havingValue = "true", matchIfMissing = true)
public class LifecycleOutboxPublisher {
    private static final Logger logger = LoggerFactory.getLogger(LifecycleOutboxPublisher.class);
    private static final int BATCH_SIZE = 200;
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final LifecycleOutboxRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public LifecycleOutboxPublisher(LifecycleOutboxRepository outbox, RabbitTemplate rabbitTemplate,
            @Value("${taskflow.messaging.exchange:taskflow.events}") String exchange) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Scheduled(fixedDelayString = "${taskflow.lifecycle-events.publish-delay-milliseconds:5000}")
    public void publishPendingEvents() {
        for (LifecycleOutboxEvent event : outbox.findAll(PageRequest.of(0, BATCH_SIZE, Sort.by("id")))) {
            try {
                publishConfirmed(event);
                outbox.delete(event);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (AmqpException | ExecutionException | TimeoutException exception) {
                logger.warn("Evento {} ({}) permanece na outbox: {}",
                        event.getEventId(), event.getEventType(), exception.getMessage());
                return;
            }
        }
    }

    private void publishConfirmed(LifecycleOutboxEvent event)
            throws InterruptedException, ExecutionException, TimeoutException {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.getEventId().toString());
        properties.setType(event.getEventType());
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.send(exchange, event.getEventType(), message, correlation);
        CorrelationData.Confirm confirmation = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!confirmation.isAck() || correlation.getReturned() != null) {
            throw new AmqpException("O broker não confirmou o roteamento do evento.");
        }
    }
}
