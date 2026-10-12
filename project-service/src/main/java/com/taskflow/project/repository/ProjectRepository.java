package com.taskflow.project.repository;

import java.util.Collection;

import com.taskflow.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Query("""
            SELECT COUNT(project) > 0 FROM Project project JOIN project.taskIds taskId
            WHERE taskId IN :taskIds
              AND (:projectId IS NULL OR project.id <> :projectId)
            """)
    boolean hasTasksAssignedToAnotherProject(@Param("taskIds") Collection<Long> taskIds,
                                            @Param("projectId") Long projectId);
}
