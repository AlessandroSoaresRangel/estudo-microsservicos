package com.taskflow.task.messaging;

public interface TaskUnlinkPublisher {
    /** Só retorna após o broker confirmar a publicação em uma fila. */
    void publish(Long taskId);
}
