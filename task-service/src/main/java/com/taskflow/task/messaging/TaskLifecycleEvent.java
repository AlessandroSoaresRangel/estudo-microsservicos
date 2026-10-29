package com.taskflow.task.messaging;

import java.time.Instant;
import java.util.UUID;

public record TaskLifecycleEvent(UUID eventId, String eventType, int schemaVersion,
        Instant occurredAt, TaskEventData data) {
    public static final int SCHEMA_VERSION = 1;
    public static final String CREATED = "task.created.v1";
    public static final String UPDATED = "task.updated.v1";
    public static final String DELETED = "task.deleted.v1";
}
