package com.taskflow.task.service;

import com.taskflow.task.entity.Task;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void findsTasksUsingTheRequestedPageable() {
        var pageable = PageRequest.of(1, 2);
        var expectedPage = new PageImpl<>(java.util.List.of(new Task("Paginada")), pageable, 3);
        when(taskRepository.findAll(pageable)).thenReturn(expectedPage);

        var page = taskService.findAll(pageable);

        assertThat(page).isEqualTo(expectedPage);
        verify(taskRepository).findAll(pageable);
    }

    @Test
    void createsTask() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var createdTask = taskService.create("Escrever testes");

        assertThat(createdTask.getTitle()).isEqualTo("Escrever testes");
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void updatesTaskTitle() {
        var existingTask = new Task("Título antigo");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(existingTask));
        when(taskRepository.save(existingTask)).thenReturn(existingTask);

        var updatedTask = taskService.update(7L, "Título novo");

        assertThat(updatedTask.getTitle()).isEqualTo("Título novo");
        verify(taskRepository).save(existingTask);
    }

    @Test
    void deletesExistingTask() {
        var existingTask = new Task("Excluir");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(existingTask));

        taskService.delete(7L);

        verify(taskRepository).delete(existingTask);
    }

    @Test
    void reportsNotFoundWhenUpdatingMissingTask() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(99L, "Título"))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("99");
    }
}
