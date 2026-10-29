package com.taskflow.project.service;

import com.taskflow.project.entity.Project;
import com.taskflow.project.messaging.ProjectLifecycleEvents;
import com.taskflow.project.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Mantém a propriedade exclusiva das tarefas no banco de projetos.
 * O bloqueio do projeto serializa vínculos com sua atualização e exclusão.
 * A restrição UNIQUE de task_id protege vínculos concorrentes entre projetos.
 */
@Service
@Transactional
public class ProjectTaskLinkService {
    private final ProjectRepository projectRepository;
    private final ProjectLifecycleEvents lifecycleEvents;

    public ProjectTaskLinkService(ProjectRepository projectRepository, ProjectLifecycleEvents lifecycleEvents) {
        this.projectRepository = projectRepository;
        this.lifecycleEvents = lifecycleEvents;
    }

    public void assign(Long projectId, Long taskId) {
        Project project = requireLockedProject(projectId);
        Long ownerId = projectRepository.findOwnerId(taskId).orElse(projectId);
        if (!ownerId.equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tarefa já vinculada a outro projeto.");
        }
        if (project.getTaskIds().contains(taskId)) {
            return;
        }
        project.addTask(taskId);
        projectRepository.flush();
        lifecycleEvents.updated(project);
    }

    /** Só deve ser chamado pelo consumidor da fila, após a exclusão local da tarefa. */
    public void release(Long taskId) {
        projectRepository.findOwnerId(taskId).ifPresent(projectId -> {
            Project project = requireLockedProject(projectId);
            if (project.getTaskIds().contains(taskId)) {
                project.removeTask(taskId);
                lifecycleEvents.updated(project);
            }
        });
    }

    private Project requireLockedProject(Long projectId) {
        return projectRepository.findLockedById(projectId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Projeto " + projectId + " não encontrado."));
    }
}
