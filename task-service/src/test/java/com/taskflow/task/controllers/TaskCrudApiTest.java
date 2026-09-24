package com.taskflow.task.controllers;

import com.jayway.jsonpath.JsonPath;
import com.taskflow.task.entity.Task;
import com.taskflow.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "management.zipkin.tracing.export.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:task-crud-api",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class TaskCrudApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void clearTasks() {
        taskRepository.deleteAll();
    }

    @Test
    void rejectsTaskTitlesShorterThanThreeCharacters() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("O título deve possuir entre 3 e 100 caracteres."))
                .andExpect(jsonPath("$.path").value("/tasks"));
    }

    @Test
    void rejectsInvalidTaskTitleWhenUpdating() throws Exception {
        var task = taskRepository.save(new Task("Título válido"));
        String title = "a".repeat(101);

        mockMvc.perform(put("/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTaskTitlesLongerThanOneHundredCharacters() throws Exception {
        String title = "a".repeat(101);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsStructuredNotFoundErrorForMissingTask() throws Exception {
        mockMvc.perform(get("/tasks/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Task with id 999 was not found"))
                .andExpect(jsonPath("$.path").value("/tasks/999"));
    }

    @Test
    void returnsRequestedTaskPageAndPaginationMetadata() throws Exception {
        taskRepository.saveAll(java.util.List.of(
                new Task("Alpha"),
                new Task("Bravo"),
                new Task("Charlie")
        ));

        mockMvc.perform(get("/tasks?page=1&size=1&sort=title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].title").value("Bravo"))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(1))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(3));
    }

    @Test
    void supportsCreatingReadingUpdatingAndDeletingTasks() throws Exception {
        var createResponse = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Primeira tarefa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Primeira tarefa"))
                .andReturn();
        Number idValue = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");
        long id = idValue.longValue();

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tarefa atualizada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tarefa atualizada"));

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].title").value("Tarefa atualizada"));

        mockMvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound());
    }
}
