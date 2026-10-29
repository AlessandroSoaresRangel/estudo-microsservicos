package com.taskflow.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@RestController
public class GatewayFallbackController {

    @RequestMapping("/fallback/tasks")
    public ResponseEntity<Map<String, String>> tasksUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("service", "api-gateway", "error", "task-service-unavailable"));
    }

    @RequestMapping("/fallback/projects")
    public ResponseEntity<Map<String, String>> projectsUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("service", "api-gateway", "error", "project-service-unavailable"));
    }
}
