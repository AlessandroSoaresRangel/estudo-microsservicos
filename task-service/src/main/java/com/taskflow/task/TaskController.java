package com.taskflow.task;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class TaskController {

    private final String instanceNumber;

    public TaskController() {
        this(System.getenv().getOrDefault("TASK_INSTANCE", "local"));
    }

    TaskController(String instanceNumber) {
        this.instanceNumber = instanceNumber;
    }

    @GetMapping("/tasks")
    public Map<String, Object> getTasks() {
        return Map.of(
                "service", "task-service",
                "instance", "instancia " + instanceNumber,
                "tasks", List.of(
                        Map.of("id", 1, "title", "Estudar microsserviços"),
                        Map.of("id", 2, "title", "Aprender API Gateway")
                )
        );
    }
}
