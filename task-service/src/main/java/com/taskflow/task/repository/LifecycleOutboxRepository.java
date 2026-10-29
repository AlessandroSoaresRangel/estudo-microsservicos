package com.taskflow.task.repository;

import com.taskflow.task.entity.LifecycleOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifecycleOutboxRepository extends JpaRepository<LifecycleOutboxEvent, Long> {
}
