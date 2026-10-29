package com.taskflow.task.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpProjectCatalog implements ProjectCatalog {
    private final ProjectServiceClient client;

    private final CircuitBreaker assignmentCircuit;

    public HttpProjectCatalog(ProjectServiceClient client, CircuitBreakerRegistry circuitBreakers) {
        this.client = client;
        this.assignmentCircuit = circuitBreakers.circuitBreaker("projectTaskAssignment");
    }

    @Override
    public void assignTask(Long projectId, Long taskId) {
        try {
            assignmentCircuit.executeRunnable(() -> client.assignTask(projectId, taskId));
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
        } catch (CallNotPermittedException exception) {
            throw unavailable(exception);
        }
    }

    private ResponseStatusException unavailable(RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível vincular as tarefas ao projeto. Tente novamente.", cause);
    }
}
