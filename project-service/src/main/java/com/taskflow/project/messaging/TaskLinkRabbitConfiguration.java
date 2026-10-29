package com.taskflow.project.messaging;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskLinkRabbitConfiguration {
    private static final String EVENT_TYPE = "task-unlink-requested.v1";

    @Bean
    public Declarables taskLinkTopology(
            @Value("${taskflow.messaging.exchange:taskflow.events}") String exchangeName,
            @Value("${taskflow.messaging.task-unlink-queue:taskflow.project.task-unlink.v1}") String queueName,
            @Value("${taskflow.messaging.task-unlink-routing-key:task.unlink-requested.v1}") String routingKey) {
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(exchangeName + ".dlx", true, false);
        Queue queue = QueueBuilder.durable(queueName).quorum()
                .deadLetterExchange(deadLetterExchange.getName()).deadLetterRoutingKey(queueName).build();
        Queue deadLetterQueue = QueueBuilder.durable(queueName + ".dlq").quorum().build();
        return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(exchange).with(routingKey),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(queueName));
    }

    @Bean
    public Declarables lifecycleTopology(
            @Value("${taskflow.messaging.exchange:taskflow.events}") String exchangeName,
            @Value("${taskflow.messaging.lifecycle-queue:taskflow.project.lifecycle.v1}") String queueName) {
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(exchangeName + ".dlx", true, false);
        Queue queue = QueueBuilder.durable(queueName).quorum()
                .deadLetterExchange(deadLetterExchange.getName()).deadLetterRoutingKey(queueName).build();
        Queue deadLetterQueue = QueueBuilder.durable(queueName + ".dlq").quorum().build();
        return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(exchange).with(ProjectLifecycleEvent.CREATED),
                BindingBuilder.bind(queue).to(exchange).with(ProjectLifecycleEvent.UPDATED),
                BindingBuilder.bind(queue).to(exchange).with(ProjectLifecycleEvent.DELETED),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(queueName));
    }

    @Bean
    public Jackson2JsonMessageConverter taskLinkMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        // O contrato usa um identificador de evento, sem acoplar os pacotes Java dos serviços.
        typeMapper.setIdClassMapping(Map.of(EVENT_TYPE, TaskUnlinkRequested.class));
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.TYPE_ID);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
