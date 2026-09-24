package com.taskflow.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taskflow.task.entity.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
