package com.taskflow.task.service;

import java.util.List;

import com.taskflow.task.messaging.TaskUnlinkPublisher;
import com.taskflow.task.messaging.TaskUnlinkPublicationException;
import com.taskflow.task.entity.PendingTaskUnlink;
import com.taskflow.task.repository.PendingTaskUnlinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskLinkCleanupTest {
    @Test
    void retriesFailedUnlinksAndOnlyAcknowledgesSuccessfulRequests() {
        PendingTaskUnlinkRepository repository = mock(PendingTaskUnlinkRepository.class);
        TaskUnlinkPublisher publisher = mock(TaskUnlinkPublisher.class);
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new PendingTaskUnlink(42L))));
        doThrow(new TaskUnlinkPublicationException("Broker indisponível", null))
                .doNothing().when(publisher).publish(42L);
        TaskLinkCleanup cleanup = new TaskLinkCleanup(repository, publisher);

        cleanup.publishPendingUnlinks();
        verify(repository, never()).deleteById(42L);
        cleanup.publishPendingUnlinks();
        verify(repository).deleteById(42L);
    }
}
