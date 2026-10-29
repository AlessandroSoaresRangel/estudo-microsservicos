package com.taskflow.project.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpTaskCatalog implements TaskCatalog {
    private final TaskServiceClient taskServiceClient;

    private final CircuitBreaker lookupCircuit;

    public HttpTaskCatalog(TaskServiceClient taskServiceClient, CircuitBreakerRegistry circuitBreakers) {
        this.taskServiceClient = taskServiceClient;
        this.lookupCircuit = circuitBreakers.circuitBreaker("taskLookup");
    }

    @Override
    public void requireExistingTask(Long taskId) {
        try {
            lookupCircuit.executeRunnable(() -> taskServiceClient.findTask(taskId));
        } catch (FeignException exception) {
            if (exception.status() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tarefa " + taskId + " não encontrada.", exception);
            }
            throw unavailable(exception);
        } catch (CallNotPermittedException exception) {
            throw unavailable(exception);
        }
    }

    private ResponseStatusException unavailable(RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível validar as tarefas. Tente novamente.", cause);
    }
}
