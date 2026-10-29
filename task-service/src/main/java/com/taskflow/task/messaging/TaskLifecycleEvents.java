package com.taskflow.task.messaging;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.entity.Task;
import com.taskflow.task.entity.LifecycleOutboxEvent;
import com.taskflow.task.repository.LifecycleOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A gravação do evento participa obrigatoriamente da transação de negócio. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class TaskLifecycleEvents {
    private final LifecycleOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public TaskLifecycleEvents(LifecycleOutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void created(Task entity) {
        record(TaskLifecycleEvent.CREATED, entity);
    }

    public void updated(Task entity) {
        record(TaskLifecycleEvent.UPDATED, entity);
    }

    public void deleted(Task entity) {
        record(TaskLifecycleEvent.DELETED, entity);
    }

    private void record(String eventType, Task entity) {
        TaskEventData data = new TaskEventData(entity.getId(), entity.getTitle(), entity.getDescription());
        TaskLifecycleEvent event = new TaskLifecycleEvent(UUID.randomUUID(), eventType,
                TaskLifecycleEvent.SCHEMA_VERSION, Instant.now(), data);
        try {
            outbox.save(new LifecycleOutboxEvent(event.eventId(), eventType, objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o evento " + eventType + ".", exception);
        }
    }
}
