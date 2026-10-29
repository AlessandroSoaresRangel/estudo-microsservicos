package com.taskflow.project.service;

import com.taskflow.project.entity.Project;
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

    public ProjectTaskLinkService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public void assign(Long projectId, Long taskId) {
        Project project = requireLockedProject(projectId);
        Long ownerId = projectRepository.findOwnerId(taskId).orElse(projectId);
        if (!ownerId.equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tarefa já vinculada a outro projeto.");
        }
        project.addTask(taskId);
        projectRepository.flush();
    }

    /** Só deve ser chamado pelo consumidor da fila, após a exclusão local da tarefa. */
    public void release(Long taskId) {
        projectRepository.findOwnerId(taskId).ifPresent(projectId -> {
            Project project = requireLockedProject(projectId);
            project.removeTask(taskId);
        });
    }

    private Project requireLockedProject(Long projectId) {
        return projectRepository.findLockedById(projectId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Projeto " + projectId + " não encontrado."));
    }
}
