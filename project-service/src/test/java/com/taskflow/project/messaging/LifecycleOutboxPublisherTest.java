package com.taskflow.project.messaging;

import java.util.List;
import java.util.UUID;

import com.taskflow.project.entity.LifecycleOutboxEvent;
import com.taskflow.project.repository.LifecycleOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LifecycleOutboxPublisherTest {
    @Test
    void removesAnEventOnlyAfterBrokerConfirmation() {
        LifecycleOutboxRepository outbox = mock(LifecycleOutboxRepository.class);
        RabbitTemplate template = mock(RabbitTemplate.class);
        LifecycleOutboxEvent event = pendingEvent(outbox);
        confirm(template, true, false);
        new LifecycleOutboxPublisher(outbox, template, "events").publishPendingEvents();
        verify(outbox).delete(event);
    }

    @Test
    void preservesAnEventAfterNegativeConfirmation() {
        LifecycleOutboxRepository outbox = mock(LifecycleOutboxRepository.class);
        RabbitTemplate template = mock(RabbitTemplate.class);
        LifecycleOutboxEvent event = pendingEvent(outbox);
        confirm(template, false, false);
        new LifecycleOutboxPublisher(outbox, template, "events").publishPendingEvents();
        verify(outbox, never()).delete(event);
        verify(template).send(eq("events"), eq("project.created.v1"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void preservesAnEventReturnedWithoutAMatchingQueue() {
        LifecycleOutboxRepository outbox = mock(LifecycleOutboxRepository.class);
        RabbitTemplate template = mock(RabbitTemplate.class);
        LifecycleOutboxEvent event = pendingEvent(outbox);
        confirm(template, true, true);
        new LifecycleOutboxPublisher(outbox, template, "events").publishPendingEvents();
        verify(outbox, never()).delete(event);
        verify(template).send(eq("events"), eq("project.created.v1"), any(Message.class), any(CorrelationData.class));
    }

    private LifecycleOutboxEvent pendingEvent(LifecycleOutboxRepository outbox) {
        LifecycleOutboxEvent event = new LifecycleOutboxEvent(UUID.randomUUID(), "project.created.v1", "{}");
        when(outbox.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event)));
        return event;
    }

    private void confirm(RabbitTemplate template, boolean acknowledged, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                        312, "NO_ROUTE", "events", "project.created.v1"));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, null));
            return null;
        }).when(template).send(eq("events"), eq("project.created.v1"), any(Message.class), any(CorrelationData.class));
    }
}
