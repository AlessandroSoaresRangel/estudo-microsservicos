package com.taskflow.task.service;

import com.taskflow.task.messaging.TaskUnlinkPublisher;
import com.taskflow.task.messaging.TaskUnlinkPublicationException;
import com.taskflow.task.repository.PendingTaskUnlinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publica a outbox no RabbitMQ; somente mensagens confirmadas são retiradas do banco. */
@Component
@ConditionalOnProperty(name = "taskflow.task-link-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class TaskLinkCleanup {
    private static final Logger logger = LoggerFactory.getLogger(TaskLinkCleanup.class);
    private static final int BATCH_SIZE = 200;

    private final PendingTaskUnlinkRepository pendingUnlinks;
    private final TaskUnlinkPublisher publisher;

    public TaskLinkCleanup(PendingTaskUnlinkRepository pendingUnlinks, TaskUnlinkPublisher publisher) {
        this.pendingUnlinks = pendingUnlinks;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${taskflow.task-link-cleanup.delay-milliseconds:5000}")
    public void publishPendingUnlinks() {
        for (var pending : pendingUnlinks.findAll(PageRequest.of(0, BATCH_SIZE))) {
            try {
                publisher.publish(pending.getTaskId());
                pendingUnlinks.deleteById(pending.getTaskId());
            } catch (TaskUnlinkPublicationException exception) {
                logger.warn("Mensagem de remoção do vínculo da tarefa {} pendente de publicação: {}",
                        pending.getTaskId(), exception.getMessage());
                // Evita percorrer todo o lote enquanto o broker está indisponível.
                return;
            }
        }
    }
}
