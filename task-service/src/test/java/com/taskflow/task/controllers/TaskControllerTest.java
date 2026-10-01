package com.taskflow.task.controllers;

import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskControllerTest {

    @Test
    void returnsTaskResponsesWithPageMetadataInAnOkResponseEntity() {
        var taskService = mock(TaskService.class);
        var pageable = PageRequest.of(0, 20);
        var expectedPage = new PageImpl<>(List.of(new TaskResponse(null, "test task")), pageable, 1);
        when(taskService.findAll(pageable)).thenReturn(expectedPage);
        var controller = new TaskController(taskService);

        ResponseEntity<Page<TaskResponse>> response = controller.getTasks(pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).containsExactly(new TaskResponse(null, "test task"));
        assertThat(response.getBody().getNumber()).isEqualTo(expectedPage.getNumber());
        assertThat(response.getBody().getSize()).isEqualTo(expectedPage.getSize());
        assertThat(response.getBody().getTotalElements()).isEqualTo(expectedPage.getTotalElements());
    }
}
