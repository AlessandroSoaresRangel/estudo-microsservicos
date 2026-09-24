package com.taskflow.task.controllers;

import com.taskflow.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskControllerTest {

    @Test
    void returnsTaskListAndInstanceMetadataInAnOkResponseEntity() {
        var taskService = mock(TaskService.class);
        var pageable = PageRequest.of(0, 20);
        when(taskService.findAll(pageable)).thenReturn(Page.empty(pageable));
        var controller = new TaskController(taskService, "2");

        ResponseEntity<Map<String, Object>> response = controller.getTasks(pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("instance", "instancia 2");
        assertThat(response.getBody()).containsEntry("tasks", List.of());
        assertThat(response.getBody()).containsEntry("pagination", Map.of(
                "page", 0,
                "size", 20,
                "totalElements", 0L,
                "totalPages", 0
        ));
    }
}
