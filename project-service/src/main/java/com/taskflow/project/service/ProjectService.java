package com.taskflow.project.service;

import java.util.LinkedHashSet;
import java.util.Set;

import com.taskflow.project.client.TaskCatalog;
import com.taskflow.project.dto.ProjectRequest;
import com.taskflow.project.dto.ProjectResponse;
import com.taskflow.project.entity.Project;
import com.taskflow.project.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final TaskCatalog taskCatalog;

    public ProjectService(ProjectRepository projectRepository, TaskCatalog taskCatalog) {
        this.projectRepository = projectRepository;
        this.taskCatalog = taskCatalog;
    }

    public Page<ProjectResponse> findAll(Pageable pageable) {
        return projectRepository.findAll(pageable).map(ProjectResponse::from);
    }

    public ProjectResponse findById(Long id) {
        return ProjectResponse.from(requireProject(id));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Set<Long> taskIds = validateTaskLinks(request, null);
        Project project = new Project(request.name(), request.description(), taskIds);
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = requireLockedProject(id);
        Set<Long> taskIds = request.taskIds() == null
                ? project.getTaskIds() : new LinkedHashSet<>(request.taskIds());
        if (!taskIds.containsAll(project.getTaskIds())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Exclua as tarefas antes de remover seus vínculos com o projeto.");
        }
        if (request.taskIds() != null) {
            taskIds = validateTaskLinks(request, id);
        }
        project.update(request.name(), request.description(), taskIds);
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public void delete(Long id) {
        Project project = requireLockedProject(id);
        if (!project.getTaskIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Projetos com tarefas não podem ser excluídos.");
        }
        projectRepository.delete(project);
    }

    private Project requireLockedProject(Long id) {
        return projectRepository.findLockedById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Projeto " + id + " não encontrado."));
    }

    private Project requireProject(Long id) {
        return projectRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Projeto " + id + " não encontrado."));
    }

    /**
     * Valida o vínculo antes da persistência local. A restrição UNIQUE no banco
     * também protege a exclusividade contra requisições concorrentes.
     * A existência remota é verificada neste instante, sem transação distribuída.
     */
    private Set<Long> validateTaskLinks(ProjectRequest request, Long projectId) {
        if (request.taskIds() == null || request.taskIds().isEmpty()) {
            return Set.of();
        }
        Set<Long> taskIds = new LinkedHashSet<>(request.taskIds());
        if (taskIds.size() != request.taskIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs de tarefas repetidos.");
        }
        if (projectRepository.hasTasksAssignedToAnotherProject(taskIds, projectId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tarefa já vinculada a outro projeto.");
        }
        taskIds.forEach(taskCatalog::requireExistingTask);
        return taskIds;
    }
}
