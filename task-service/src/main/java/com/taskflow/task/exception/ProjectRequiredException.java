package com.taskflow.task.exception;

public class ProjectRequiredException extends RuntimeException {
    public ProjectRequiredException() {
        super("Informe o campo projectId com o ID de um projeto existente.");
    }
}
