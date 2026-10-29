package com.taskflow.project.messaging;

import java.time.Instant;
import java.util.UUID;

public record ProjectLifecycleEvent(UUID eventId, String eventType, int schemaVersion,
        Instant occurredAt, ProjectEventData data) {
    public static final int SCHEMA_VERSION = 1;
    public static final String CREATED = "project.created.v1";
    public static final String UPDATED = "project.updated.v1";
    public static final String DELETED = "project.deleted.v1";
}
