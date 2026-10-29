package com.taskflow.project.messaging;

import com.taskflow.project.service.ProjectTaskLinkService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TaskUnlinkListener {
    private final ProjectTaskLinkService linkService;

    public TaskUnlinkListener(ProjectTaskLinkService linkService) {
        this.linkService = linkService;
    }

    /** O serviço confirma sua transação antes do retorno; o container só então envia o ACK. */
    @RabbitListener(queues = "${taskflow.messaging.task-unlink-queue:taskflow.project.task-unlink.v1}")
    public void onTaskUnlinkRequested(TaskUnlinkRequested event) {
        if (event == null || event.taskId() == null || event.taskId() <= 0) {
            throw new AmqpRejectAndDontRequeueException("A mensagem deve informar um taskId positivo.");
        }
        // release é idempotente: reentregas após falhas não alteram o resultado.
        linkService.release(event.taskId());
    }
}
