package com.taskflow.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
                @NotBlank(message = "O título é obrigatório.") @Size(min = 3, max = 100, message = "O título deve possuir entre 3 e 100 caracteres.") String title) {
}
