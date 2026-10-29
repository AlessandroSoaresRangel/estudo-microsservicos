package com.taskflow.project.repository;

import com.taskflow.project.entity.LifecycleOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifecycleOutboxRepository extends JpaRepository<LifecycleOutboxEvent, Long> {
}
