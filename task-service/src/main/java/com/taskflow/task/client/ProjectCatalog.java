package com.taskflow.task.client;

public interface ProjectCatalog {
    void assignTask(Long projectId, Long taskId);
    void releaseTask(Long taskId);
}
