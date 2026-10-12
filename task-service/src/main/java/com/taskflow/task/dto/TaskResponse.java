package com.taskflow.task.dto;

import com.taskflow.task.entity.Task;

public record TaskResponse(Long id, String title, String description) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription());
    }
}
