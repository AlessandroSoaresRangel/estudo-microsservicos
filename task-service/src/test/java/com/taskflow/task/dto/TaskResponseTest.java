package com.taskflow.task.dto;

import com.taskflow.task.entity.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskResponseTest {

    @Test
    void exposesTaskIdAndTitle() {
        var task = mock(Task.class);
        when(task.getId()).thenReturn(42L);
        when(task.getTitle()).thenReturn("Revisar DTO");

        var response = TaskResponse.from(task);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.title()).isEqualTo("Revisar DTO");
    }
}
