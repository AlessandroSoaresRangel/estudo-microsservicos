package com.taskflow.project;

import com.jayway.jsonpath.JsonPath;
import com.taskflow.project.client.TaskCatalog;
import com.taskflow.project.repository.ProjectRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
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

    @BeforeEach
    void clearProjects() {
        projectRepository.deleteAll();
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
        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskIds.length()").value(2));
        mockMvc.perform(put("/projects/{id}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Projeto atualizado","taskIds":[103]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskIds[0]").value(103));
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
                        .content("{\"name\":\"Projeto modificado\",\"taskIds\":[999]}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Projeto original"))
                .andExpect(jsonPath("$.description").value("Original"))
                .andExpect(jsonPath("$.taskIds[0]").value(501));
    }

    @Test
    void requiresAtLeastOneTask() throws Exception {
        mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Projeto vazio","taskIds":[]}
                                """))
                .andExpect(status().isBadRequest());
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
