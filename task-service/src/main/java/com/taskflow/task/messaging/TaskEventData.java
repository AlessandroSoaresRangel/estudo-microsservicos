package com.taskflow.task.messaging;

public record TaskEventData(Long taskId, String title, String description) {
}
