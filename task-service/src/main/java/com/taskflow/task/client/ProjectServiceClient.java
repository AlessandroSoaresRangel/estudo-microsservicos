package com.taskflow.task.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "project-service")
public interface ProjectServiceClient {
    @PostMapping("/internal/project-task-links/{projectId}/{taskId}")
    void assignTask(@PathVariable("projectId") Long projectId, @PathVariable("taskId") Long taskId);

}
