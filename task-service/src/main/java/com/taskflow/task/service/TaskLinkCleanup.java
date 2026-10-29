package com.taskflow.task.service;

import com.taskflow.task.client.ProjectCatalog;
import com.taskflow.task.repository.PendingTaskUnlinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Outbox persistente: uma falha remota mantém o vínculo para uma nova tentativa. */
@Component
@ConditionalOnProperty(name = "taskflow.task-link-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class TaskLinkCleanup {
    private static final Logger logger = LoggerFactory.getLogger(TaskLinkCleanup.class);
    private static final int BATCH_SIZE = 200;

    private final PendingTaskUnlinkRepository pendingUnlinks;
    private final ProjectCatalog projectCatalog;

    public TaskLinkCleanup(PendingTaskUnlinkRepository pendingUnlinks, ProjectCatalog projectCatalog) {
        this.pendingUnlinks = pendingUnlinks;
        this.projectCatalog = projectCatalog;
    }

    @Scheduled(fixedDelayString = "${taskflow.task-link-cleanup.delay-milliseconds:5000}")
    public void releaseDeletedTasks() {
        for (var pending : pendingUnlinks.findAll(PageRequest.of(0, BATCH_SIZE))) {
            try {
                projectCatalog.releaseTask(pending.getTaskId());
                pendingUnlinks.deleteById(pending.getTaskId());
            } catch (ResponseStatusException exception) {
                logger.warn("Vínculo da tarefa {} pendente de remoção: {}",
                        pending.getTaskId(), exception.getReason());
            }
        }
    }
}
