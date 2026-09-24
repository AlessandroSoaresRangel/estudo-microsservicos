package com.taskflow.task.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.taskflow.task.entity.Task;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-repository-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void savesAndLoadsTaskById() {
        var savedTask = taskRepository.save(new Task("Criar repository"));

        var loadedTask = taskRepository.findById(savedTask.getId());

        assertThat(loadedTask).isPresent();
        assertThat(loadedTask.get().getTitle()).isEqualTo("Criar repository");
    }
}
