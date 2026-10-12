package com.taskflow.project.dto;

import java.util.List;

import com.taskflow.project.entity.Project;

public record ProjectResponse(Long id, String name, String description, List<Long> taskIds) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getTaskIds().stream().sorted().toList());
    }
}
