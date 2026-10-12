package com.taskflow.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.taskflow.task.validation.TaskValidationLimits.MAX_TITLE_LENGTH_CHARACTERS;
import static com.taskflow.task.validation.TaskValidationLimits.MIN_TITLE_LENGTH_CHARACTERS;

public record TaskRequest(
        @NotBlank(message = "O título é obrigatório.")
        @Size(min = MIN_TITLE_LENGTH_CHARACTERS, max = MAX_TITLE_LENGTH_CHARACTERS,
                message = "O título deve possuir entre 3 e 100 caracteres.")
        String title,
        String description) {
}
