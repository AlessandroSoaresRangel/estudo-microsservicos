package com.taskflow.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GatewayFallbackController {

    @GetMapping("/fallback/tasks")
    public ResponseEntity<Map<String, String>> tasksUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("service", "api-gateway", "error", "task-service-unavailable"));
    }

    @GetMapping("/fallback/health")
    public ResponseEntity<Map<String, String>> healthUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("service", "api-gateway", "error", "task-service-health-unavailable"));
    }
}
