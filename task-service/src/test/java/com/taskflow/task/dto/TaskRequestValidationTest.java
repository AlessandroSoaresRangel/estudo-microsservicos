package com.taskflow.task.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRequestValidationTest {

    @Test
    void reportsThatTitleIsRequiredWhenItIsMissing() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new TaskRequest(null, null, 1L));

            assertThat(violations)
                    .extracting("message")
                    .contains("O título é obrigatório.");
        }
    }

    @Test
    void reportsTheAllowedTitleLength() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new TaskRequest("ab", null, 1L));

            assertThat(violations)
                    .extracting("message")
                    .contains("O título deve possuir entre 3 e 100 caracteres.");
        }
    }
}
