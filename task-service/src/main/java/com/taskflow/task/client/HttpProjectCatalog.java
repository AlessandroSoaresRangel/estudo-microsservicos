package com.taskflow.task.client;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpProjectCatalog implements ProjectCatalog {
    private final ProjectServiceClient client;

    public HttpProjectCatalog(ProjectServiceClient client) {
        this.client = client;
    }

    @Override
    public void assignTask(Long projectId, Long taskId) {
        try {
            client.assignTask(projectId, taskId);
        } catch (FeignException exception) {
            if (exception.status() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Projeto " + projectId + " não encontrado.", exception);
            }
            if (exception.status() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A tarefa já pertence a outro projeto.", exception);
            }
            throw unavailable(exception);
        }
    }

    @Override
    public void releaseTask(Long taskId) {
        try {
            client.releaseTask(taskId);
        } catch (FeignException exception) {
            throw unavailable(exception);
        }
    }

    private ResponseStatusException unavailable(FeignException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível vincular as tarefas ao projeto. Tente novamente.", cause);
    }
}
