package com.taskflow.project.messaging;

import com.taskflow.project.service.ProjectTaskLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TaskUnlinkListenerTest {
    @Test
    void delegatesTheTaskUnlinkToTheTransactionalService() {
        ProjectTaskLinkService service = mock(ProjectTaskLinkService.class);
        new TaskUnlinkListener(service).onTaskUnlinkRequested(new TaskUnlinkRequested(42L));
        verify(service).release(42L);
    }

    @Test
    void rejectsInvalidMessagesWithoutChangingLinks() {
        ProjectTaskLinkService service = mock(ProjectTaskLinkService.class);
        TaskUnlinkListener listener = new TaskUnlinkListener(service);
        assertThatThrownBy(() -> listener.onTaskUnlinkRequested(new TaskUnlinkRequested(null)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onTaskUnlinkRequested(new TaskUnlinkRequested(-1L)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(service);
    }
}
