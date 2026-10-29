package com.taskflow.project.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "lifecycle_event_outbox")
public class LifecycleOutboxEvent {
    private static final int MAX_EVENT_TYPE_LENGTH_CHARACTERS = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = MAX_EVENT_TYPE_LENGTH_CHARACTERS)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    protected LifecycleOutboxEvent() {
    }

    public LifecycleOutboxEvent(UUID eventId, String eventType, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }
}
