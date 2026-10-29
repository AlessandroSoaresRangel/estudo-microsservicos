package com.taskflow.task.service;

import com.taskflow.task.client.ProjectCatalog;
import com.taskflow.task.messaging.TaskLifecycleEvents;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.entity.PendingTaskUnlink;
import com.taskflow.task.entity.Task;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.exception.ProjectRequiredException;
import com.taskflow.task.repository.PendingTaskUnlinkRepository;
import com.taskflow.task.repository.TaskRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectCatalog projectCatalog;
    private final PendingTaskUnlinkRepository pendingUnlinks;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskLifecycleEvents lifecycleEvents;

    public TaskService(TaskRepository taskRepository, ProjectCatalog projectCatalog,
                       PendingTaskUnlinkRepository pendingUnlinks,
                       ApplicationEventPublisher eventPublisher, TaskLifecycleEvents lifecycleEvents) {
        this.taskRepository = taskRepository;
        this.projectCatalog = projectCatalog;
        this.pendingUnlinks = pendingUnlinks;
        this.eventPublisher = eventPublisher;
        this.lifecycleEvents = lifecycleEvents;
    }

    public Page<TaskResponse> findAll(Pageable pageable) {
        return taskRepository.findAll(pageable).map(TaskResponse::from);
    }

    public TaskResponse findById(Long id) {
        return TaskResponse.from(findTaskById(id));
    }

    @Transactional
    public TaskResponse create(String title, String description, Long projectId) {
        requireProjectId(projectId);
        Task task = taskRepository.save(new Task(title, description));
        eventPublisher.publishEvent(new TaskLinkAttempted(task.getId()));
        // O vínculo remoto é confirmado antes do commit local. Falhas revertem a criação.
        projectCatalog.assignTask(projectId, task.getId());
        lifecycleEvents.created(task);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse update(Long id, String title, String description, Long projectId) {
        requireProjectId(projectId);
        var task = findTaskById(id);
        // Atribuição idempotente confirma o dono; não permite mover a tarefa para outro projeto.
        projectCatalog.assignTask(projectId, id);
        task.setTitle(title);
        task.setDescription(description);
        Task savedTask = taskRepository.save(task);
        lifecycleEvents.updated(savedTask);
        return TaskResponse.from(savedTask);
    }

    @Transactional
    public void delete(Long id) {
        Task task = findTaskById(id);
        lifecycleEvents.deleted(task);
        taskRepository.delete(task);
        // A fila e a exclusão são persistidas na mesma transação. O consumidor só vê commits.
        pendingUnlinks.save(new PendingTaskUnlink(id));
    }

    private void requireProjectId(Long projectId) {
        if (projectId == null) {
            throw new ProjectRequiredException();
        }
        if (projectId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um projeto com ID positivo.");
        }
    }

    private Task findTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
