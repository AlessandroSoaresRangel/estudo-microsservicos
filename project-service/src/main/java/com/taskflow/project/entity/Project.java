package com.taskflow.project.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import static com.taskflow.project.validation.ProjectValidationLimits.MAX_NAME_LENGTH_CHARACTERS;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_NAME_LENGTH_CHARACTERS)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "project_tasks", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "task_id", nullable = false)
    private Set<Long> taskIds = new LinkedHashSet<>();

    protected Project() {
    }

    public Project(String name, String description, Set<Long> taskIds) {
        update(name, description, taskIds);
    }

    public void update(String name, String description, Set<Long> taskIds) {
        this.name = name;
        this.description = description;
        this.taskIds.clear();
        this.taskIds.addAll(taskIds);
    }

    public void addTask(Long taskId) {
        taskIds.add(taskId);
    }

    public void removeTask(Long taskId) {
        taskIds.remove(taskId);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<Long> getTaskIds() {
        return Set.copyOf(taskIds);
    }
}
