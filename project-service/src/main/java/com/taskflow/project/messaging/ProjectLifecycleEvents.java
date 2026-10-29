package com.taskflow.project.messaging;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.project.entity.Project;
import com.taskflow.project.entity.LifecycleOutboxEvent;
import com.taskflow.project.repository.LifecycleOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A gravação do evento participa obrigatoriamente da transação de negócio. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ProjectLifecycleEvents {
    private final LifecycleOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public ProjectLifecycleEvents(LifecycleOutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void created(Project entity) {
        record(ProjectLifecycleEvent.CREATED, entity);
    }

    public void updated(Project entity) {
        record(ProjectLifecycleEvent.UPDATED, entity);
    }

    public void deleted(Project entity) {
        record(ProjectLifecycleEvent.DELETED, entity);
    }

    private void record(String eventType, Project entity) {
        ProjectEventData data = new ProjectEventData(entity.getId(), entity.getName(), entity.getDescription(),
                entity.getTaskIds().stream().sorted().toList());
        ProjectLifecycleEvent event = new ProjectLifecycleEvent(UUID.randomUUID(), eventType,
                ProjectLifecycleEvent.SCHEMA_VERSION, Instant.now(), data);
        try {
            outbox.save(new LifecycleOutboxEvent(event.eventId(), eventType, objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o evento " + eventType + ".", exception);
        }
    }
}
