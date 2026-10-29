package com.taskflow.task.messaging;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitTaskUnlinkPublisher implements TaskUnlinkPublisher {
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public RabbitTaskUnlinkPublisher(RabbitTemplate rabbitTemplate,
            @Value("${taskflow.messaging.exchange:taskflow.events}") String exchange,
            @Value("${taskflow.messaging.task-unlink-routing-key:task.unlink-requested.v1}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    /**
     * A outbox só pode ser removida após confirmação positiva e ausência de retorno.
     * Uma falha ou resposta perdida mantém a pendência; entregas duplicadas são esperadas.
     */
    @Override
    public void publish(Long taskId) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, new TaskUnlinkRequested(taskId), correlation);
            CorrelationData.Confirm confirmation = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!confirmation.isAck() || correlation.getReturned() != null) {
                throw new TaskUnlinkPublicationException("O broker não confirmou o roteamento da mensagem.", null);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw publicationFailure(exception);
        } catch (AmqpException | ExecutionException | TimeoutException exception) {
            throw publicationFailure(exception);
        }
    }

    private TaskUnlinkPublicationException publicationFailure(Exception cause) {
        return new TaskUnlinkPublicationException("Não foi possível publicar a remoção do vínculo.", cause);
    }
}
