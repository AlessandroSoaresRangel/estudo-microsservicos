package com.taskflow.task.service;

import com.taskflow.task.entity.PendingTaskUnlink;
import com.taskflow.task.repository.PendingTaskUnlinkRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FailedTaskLinkCleanup {
    private final PendingTaskUnlinkRepository pendingUnlinks;

    public FailedTaskLinkCleanup(PendingTaskUnlinkRepository pendingUnlinks) {
        this.pendingUnlinks = pendingUnlinks;
    }

    /**
     * Uma resposta perdida pode deixar um vínculo remoto mesmo com rollback local.
     * Usa outra transação porque a transação de criação já foi revertida.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueCompensation(TaskLinkAttempted event) {
        pendingUnlinks.save(new PendingTaskUnlink(event.taskId()));
    }
}
