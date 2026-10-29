package com.taskflow.project.messaging;

public record ProjectEventData(Long projectId, String name, String description, java.util.List<Long> taskIds) {
}
