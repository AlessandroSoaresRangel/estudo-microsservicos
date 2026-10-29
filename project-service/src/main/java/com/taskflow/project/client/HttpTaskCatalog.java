package com.taskflow.project.client;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpTaskCatalog implements TaskCatalog {
    private final TaskServiceClient taskServiceClient;

    public HttpTaskCatalog(TaskServiceClient taskServiceClient) {
        this.taskServiceClient = taskServiceClient;
    }

    @Override
    public void requireExistingTask(Long taskId) {
        try {
            taskServiceClient.findTask(taskId);
        } catch (FeignException exception) {
            if (exception.status() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tarefa " + taskId + " não encontrada.", exception);
            }
            throw unavailable(exception);
        }
    }

    private ResponseStatusException unavailable(FeignException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível validar as tarefas. Tente novamente.", cause);
    }
}
