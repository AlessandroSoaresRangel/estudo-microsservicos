package com.taskflow.task.controllers;

import com.taskflow.task.dto.TaskRequest;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.service.TaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final String instanceNumber;

    public TaskController(TaskService taskService, @Value("${TASK_INSTANCE:local}") String instanceNumber) {
        this.taskService = taskService;
        this.instanceNumber = instanceNumber;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTasks(Pageable pageable) {
        var taskPage = taskService.findAll(pageable);
        return ResponseEntity.ok(Map.of(
                "service", "task-service",
                "instance", "instancia " + instanceNumber,
                "tasks", taskPage.getContent().stream().map(TaskResponse::from).toList(),
                "pagination", Map.of(
                        "page", taskPage.getNumber(),
                        "size", taskPage.getSize(),
                        "totalElements", taskPage.getTotalElements(),
                        "totalPages", taskPage.getTotalPages()
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(TaskResponse.from(taskService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(taskService.create(request.title())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(TaskResponse.from(taskService.update(id, request.title())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
