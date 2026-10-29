package com.taskflow.task.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pending_task_unlinks")
public class PendingTaskUnlink {
    @Id
    private Long taskId;

    protected PendingTaskUnlink() {
    }

    public PendingTaskUnlink(Long taskId) {
        this.taskId = taskId;
    }

    public Long getTaskId() {
        return taskId;
    }
}
