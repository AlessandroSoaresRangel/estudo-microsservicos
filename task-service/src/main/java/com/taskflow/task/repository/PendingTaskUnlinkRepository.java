package com.taskflow.task.repository;

import com.taskflow.task.entity.PendingTaskUnlink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingTaskUnlinkRepository extends JpaRepository<PendingTaskUnlink, Long> {
}
