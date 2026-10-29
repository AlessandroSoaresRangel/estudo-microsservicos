package com.taskflow.project;

import com.jayway.jsonpath.JsonPath;
import com.taskflow.project.client.TaskCatalog;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.project.repository.LifecycleOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import org.springframework.jdbc.core.JdbcTemplate;
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
        "management.tracing.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:project-api;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
class ProjectApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskCatalog taskCatalog;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LifecycleOutboxRepository lifecycleOutbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearProjects() {
        lifecycleOutbox.deleteAll();
        jdbcTemplate.update("DELETE FROM project_tasks");
        jdbcTemplate.update("DELETE FROM projects");
    }

    @Test
    void recordsProjectCreationUpdateAndDeletionInTheOutbox() throws Exception {
        String response = mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto com eventos\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(response, "$.id");
        mockMvc.perform(put("/projects/{id}", projectId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto atualizado\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/projects/{id}", projectId)).andExpect(status().isNoContent());
        assertThat(lifecycleOutbox.findAll(org.springframework.data.domain.Sort.by("id")))
                .extracting(com.taskflow.project.entity.LifecycleOutboxEvent::getEventType)
                .containsExactly("project.created.v1", "project.updated.v1", "project.deleted.v1");
    }

    @Test
    void doesNotExposeTheRemovedOrphanImportEndpoint() throws Exception {
        mockMvc.perform(post("/internal/project-task-links/import")
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void assignsATaskIdempotentlyAndRejectsAnotherOwner() throws Exception {
        String firstResponse = mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Primeiro projeto\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number firstId = JsonPath.read(firstResponse, "$.id");
        String secondResponse = mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Segundo projeto\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number secondId = JsonPath.read(secondResponse, "$.id");
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/internal/project-task-links/{projectId}/905", firstId))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/internal/project-task-links/{projectId}/905", secondId))
                .andExpect(status().isConflict());
        assertThat(lifecycleOutbox.findAll(org.springframework.data.domain.Sort.by("id")))
                .extracting(com.taskflow.project.entity.LifecycleOutboxEvent::getEventType)
                .containsExactly("project.created.v1", "project.created.v1", "project.updated.v1");
        mockMvc.perform(put("/projects/{id}", firstId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nome atualizado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskIds[0]").value(905));
        mockMvc.perform(delete("/projects/{id}", firstId)).andExpect(status().isConflict());
    }

    @Test
    void rejectsRemovingTaskLinksWhileUpdatingAProject() throws Exception {
        String response = mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto protegido\",\"taskIds\":[902]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(response, "$.id");
        mockMvc.perform(put("/projects/{id}", projectId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto protegido\",\"taskIds\":[]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void preventsDeletingAProjectThatStillOwnsTasks() throws Exception {
        String response = mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto protegido\",\"taskIds\":[901]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(response, "$.id");
        mockMvc.perform(delete("/projects/{id}", projectId)).andExpect(status().isConflict());
        assertThat(projectRepository.existsById(projectId.longValue())).isTrue();
    }

    @Test
    void createsReadsUpdatesAndDeletesAProjectWithMultipleTasks() throws Exception {
        String response = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Projeto inicial","description":"Detalhes","taskIds":[101,102]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Projeto inicial"))
                .andExpect(jsonPath("$.taskIds.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(response, "$.id");
        verify(taskCatalog).requireExistingTask(101L);
        verify(taskCatalog).requireExistingTask(102L);

        mockMvc.perform(get("/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Detalhes"));
        mockMvc.perform(get("/projects?sort=id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskIds.length()").value(2));
        mockMvc.perform(put("/projects/{id}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Projeto atualizado","taskIds":[101,102,103]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskIds[2]").value(103));
        mockMvc.perform(delete("/projects/{id}", projectId)).andExpect(status().isConflict());
        for (long taskId : new long[] {101, 102, 103}) {
            mockMvc.perform(delete("/internal/project-task-links/{taskId}", taskId))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(delete("/projects/{id}", projectId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/projects/{id}", projectId)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingTasksAndDoesNotPersistTheProject() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tarefa inexistente"))
                .when(taskCatalog).requireExistingTask(999L);
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto inválido\",\"taskIds\":[999]}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(projectRepository.count()).isZero();
        assertThat(lifecycleOutbox.count()).isZero();
    }

    @Test
    void reportsTaskServiceUnavailability() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço indisponível"))
                .when(taskCatalog).requireExistingTask(888L);
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto indisponível\",\"taskIds\":[888]}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsRepeatedTaskIds() throws Exception {
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto duplicado\",\"taskIds\":[301,301]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankProjectNamesWithValidTaskIds() throws Exception {
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"taskIds\":[401]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeTaskIdsWithAValidProjectName() throws Exception {
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto válido\",\"taskIds\":[-1]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsProjectNamesLongerThanTheAllowedLimit() throws Exception {
        String name = "a".repeat(101);
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"taskIds\":[401]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preservesTheProjectWhenAnUpdatedTaskDoesNotExist() throws Exception {
        String response = mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto original\",\"description\":\"Original\",\"taskIds\":[501]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(response, "$.id");
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tarefa inexistente"))
                .when(taskCatalog).requireExistingTask(999L);

        mockMvc.perform(put("/projects/{id}", projectId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projeto modificado\",\"taskIds\":[501,999]}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Projeto original"))
                .andExpect(jsonPath("$.description").value("Original"))
                .andExpect(jsonPath("$.taskIds[0]").value(501));
    }

    @Test
    void allowsCreatingAProjectBeforeItsTasks() throws Exception {
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Projeto vazio","taskIds":[]}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void preventsATaskFromBelongingToTwoProjects() throws Exception {
        String body = """
                {"name":"Projeto exclusivo","taskIds":[201]}
                """;
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
}
