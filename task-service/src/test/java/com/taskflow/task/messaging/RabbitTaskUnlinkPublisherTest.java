package com.taskflow.task.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitTaskUnlinkPublisherTest {
    @Test
    void onlyCompletesAfterAPositiveBrokerConfirmation() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        confirmPublication(template, true, false);
        new RabbitTaskUnlinkPublisher(template, "events", "unlink").publish(42L);
        verify(template).convertAndSend(eq("events"), eq("unlink"), eq(new TaskUnlinkRequested(42L)),
                any(CorrelationData.class));
    }

    @Test
    void rejectsANegativeBrokerConfirmation() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        confirmPublication(template, false, false);
        assertThatThrownBy(() -> new RabbitTaskUnlinkPublisher(template, "events", "unlink").publish(42L))
                .isInstanceOf(TaskUnlinkPublicationException.class);
    }

    @Test
    void rejectsMessagesReturnedBecauseThereIsNoMatchingQueue() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        confirmPublication(template, true, true);
        assertThatThrownBy(() -> new RabbitTaskUnlinkPublisher(template, "events", "unlink").publish(42L))
                .isInstanceOf(TaskUnlinkPublicationException.class);
    }

    private void confirmPublication(RabbitTemplate template, boolean acknowledged, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                        312, "NO_ROUTE", "events", "unlink"));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, null));
            return null;
        }).when(template).convertAndSend(eq("events"), eq("unlink"), any(TaskUnlinkRequested.class),
                any(CorrelationData.class));
    }
}
