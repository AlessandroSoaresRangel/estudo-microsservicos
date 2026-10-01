package com.taskflow.task.service;

import com.taskflow.task.dto.TaskResponse;
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

    public Page<TaskResponse> findAll(Pageable pageable) {
        return taskRepository.findAll(pageable).map(TaskResponse::from);
    }

    public TaskResponse findById(Long id) {
        return TaskResponse.from(findTaskById(id));
    }

    @Transactional
    public TaskResponse create(String title) {
        return TaskResponse.from(taskRepository.save(new Task(title)));
    }

    @Transactional
    public TaskResponse update(Long id, String title) {
        var task = findTaskById(id);
        task.setTitle(title);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.delete(findTaskById(id));
    }

    private Task findTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
