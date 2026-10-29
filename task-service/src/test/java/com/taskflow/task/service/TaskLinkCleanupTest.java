package com.taskflow.task.service;

import java.util.List;

import com.taskflow.task.client.ProjectCatalog;
import com.taskflow.task.entity.PendingTaskUnlink;
import com.taskflow.task.repository.PendingTaskUnlinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        ProjectCatalog catalog = mock(ProjectCatalog.class);
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new PendingTaskUnlink(42L))));
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .doNothing().when(catalog).releaseTask(42L);
        TaskLinkCleanup cleanup = new TaskLinkCleanup(repository, catalog);

        cleanup.releaseDeletedTasks();
        verify(repository, never()).deleteById(42L);
        cleanup.releaseDeletedTasks();
        verify(repository).deleteById(42L);
    }
}
