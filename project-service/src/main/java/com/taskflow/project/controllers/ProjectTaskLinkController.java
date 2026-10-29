package com.taskflow.project.controllers;

import com.taskflow.project.service.ProjectTaskLinkService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API interna: não é publicada nas rotas do Gateway. */
@RestController
@RequestMapping("/internal/project-task-links")
public class ProjectTaskLinkController {
    private final ProjectTaskLinkService linkService;

    public ProjectTaskLinkController(ProjectTaskLinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping("/{projectId}/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable @Positive Long projectId, @PathVariable @Positive Long taskId) {
        linkService.assign(projectId, taskId);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable @Positive Long taskId) {
        linkService.release(taskId);
    }

}
