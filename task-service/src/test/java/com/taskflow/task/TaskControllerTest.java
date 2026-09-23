package com.taskflow.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskControllerTest {

    @Test
    void includesConfiguredInstanceNumberInTasksResponse() {
        var response = new TaskController("2").getTasks();

        assertThat(response.get("instance")).isEqualTo("instancia 2");
    }
}
