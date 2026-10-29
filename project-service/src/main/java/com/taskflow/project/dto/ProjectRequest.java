package com.taskflow.project.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import static com.taskflow.project.validation.ProjectValidationLimits.MAX_NAME_LENGTH_CHARACTERS;

public record ProjectRequest(
        @NotBlank
        @Size(max = MAX_NAME_LENGTH_CHARACTERS)
        String name,
        String description,
        List<@NotNull @Positive Long> taskIds) {
}
