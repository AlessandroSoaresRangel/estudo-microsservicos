package com.taskflow.task.service;

import com.taskflow.task.entity.Task;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Page<Task> findAll(Pageable pageable) {
        return taskRepository.findAll(pageable);
    }

    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional
    public Task create(String title) {
        return taskRepository.save(new Task(title));
    }

    @Transactional
    public Task update(Long id, String title) {
        var task = findById(id);
        task.setTitle(title);
        return taskRepository.save(task);
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.delete(findById(id));
    }
}
