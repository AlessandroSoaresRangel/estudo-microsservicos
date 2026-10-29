package com.taskflow.project.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "task-service")
public interface TaskServiceClient {
    @GetMapping("/tasks/{id}")
    void findTask(@PathVariable("id") Long taskId);
}
