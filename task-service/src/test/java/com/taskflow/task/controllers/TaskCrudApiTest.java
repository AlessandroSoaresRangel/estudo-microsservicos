package com.taskflow.task.controllers;

import com.jayway.jsonpath.JsonPath;
import com.taskflow.task.entity.Task;
import com.taskflow.task.client.ProjectCatalog;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.task.repository.LifecycleOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        "taskflow.lifecycle-events.publisher-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false",
                "taskflow.task-link-cleanup.enabled=false",
        "management.zipkin.tracing.export.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:task-crud-api",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class TaskCrudApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCatalog projectCatalog;

    @Autowired
    private com.taskflow.task.repository.PendingTaskUnlinkRepository pendingUnlinks;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private LifecycleOutboxRepository lifecycleOutbox;

    @BeforeEach
    void clearTasks() {
        lifecycleOutbox.deleteAll();
        taskRepository.deleteAll();
        pendingUnlinks.deleteAll();
    }

    @Test
    void rollsBackTaskCreationWhenTheProjectDoesNotExist() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Projeto inexistente"))
                .when(projectCatalog).assignTask(eq(999L), anyLong());
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tarefa inválida\",\"projectId\":999}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(taskRepository.count()).isZero();
        assertThat(lifecycleOutbox.count()).isZero();
    }

    @Test
    void rollsBackTaskCreationWhenTheProjectServiceIsUnavailable() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço indisponível"))
                .when(projectCatalog).assignTask(eq(1L), anyLong());
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tarefa indisponível\",\"projectId\":1}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(taskRepository.count()).isZero();
        assertThat(lifecycleOutbox.count()).isZero();
        assertThat(pendingUnlinks.count()).isEqualTo(1);
    }

    @Test
    void rejectsUpdatingATaskWithoutAProjectAndPreservesItsContent() throws Exception {
        Task task = taskRepository.save(new Task("Título original"));
        mockMvc.perform(put("/tasks/{id}", task.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Título alterado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_REQUIRED"));
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTitle()).isEqualTo("Título original");
    }

    @Test
    void rejectsMovingATaskToAnotherProject() throws Exception {
        Task task = taskRepository.save(new Task("Título original"));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Outro projeto"))
                .when(projectCatalog).assignTask(2L, task.getId());
        mockMvc.perform(put("/tasks/{id}", task.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Título alterado\",\"projectId\":2}"))
                .andExpect(status().isConflict());
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTitle()).isEqualTo("Título original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tasks", "/tasks/"})
    void rejectsCreatingATaskWithoutAProject(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tarefa sem projeto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_REQUIRED"))
                .andExpect(jsonPath("$.field").value("projectId"))
                .andExpect(jsonPath("$.message").value("Informe o campo projectId com o ID de um projeto existente."));
        org.assertj.core.api.Assertions.assertThat(taskRepository.count()).isZero();
    }

    @Test
    void rejectsAnExplicitlyNullProject() throws Exception {
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tarefa sem projeto\",\"projectId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_REQUIRED"))
                .andExpect(jsonPath("$.field").value("projectId"));
        assertThat(taskRepository.count()).isZero();
        assertThat(lifecycleOutbox.count()).isZero();
    }

    @Test
    void acceptsTasksWithoutDescription() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"title\":\"Sem descrição\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Sem descrição"));
    }

    @Test
    void rejectsTaskTitlesShorterThanThreeCharacters() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"title\":\"ab\"}"))
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
                        .content("{\"projectId\":1,\"title\":\"" + title + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTaskTitlesLongerThanOneHundredCharacters() throws Exception {
        String title = "a".repeat(101);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"title\":\"" + title + "\"}"))
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
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Bravo"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void supportsCreatingReadingUpdatingAndDeletingTasks() throws Exception {
        var createResponse = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"title\":\"Primeira tarefa\",\"description\":\"Detalhes da tarefa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Primeira tarefa"))
                .andExpect(jsonPath("$.description").value("Detalhes da tarefa"))
                .andReturn();
        Number idValue = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");
        long id = idValue.longValue();
        verify(projectCatalog).assignTask(1L, id);

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.description").value("Detalhes da tarefa"));

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"title\":\"Tarefa atualizada\",\"description\":\"Descrição atualizada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tarefa atualizada"))
                .andExpect(jsonPath("$.description").value("Descrição atualizada"));

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Tarefa atualizada"))
                .andExpect(jsonPath("$.content[0].description").value("Descrição atualizada"));

        mockMvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());
        assertThat(pendingUnlinks.existsById(id)).isTrue();

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound());
        assertThat(lifecycleOutbox.findAll(org.springframework.data.domain.Sort.by("id")))
                .extracting(com.taskflow.task.entity.LifecycleOutboxEvent::getEventType)
                .containsExactly("task.created.v1", "task.updated.v1", "task.deleted.v1");
    }
}
