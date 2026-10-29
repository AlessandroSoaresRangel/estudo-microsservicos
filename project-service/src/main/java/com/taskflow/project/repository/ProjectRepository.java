package com.taskflow.project.repository;

import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import com.taskflow.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT project FROM Project project WHERE project.id = :id")
    Optional<Project> findLockedById(@Param("id") Long id);

    @Query("SELECT project.id FROM Project project JOIN project.taskIds taskId WHERE taskId = :taskId")
    Optional<Long> findOwnerId(@Param("taskId") Long taskId);

    @Query("""
            SELECT COUNT(project) > 0 FROM Project project JOIN project.taskIds taskId
            WHERE taskId IN :taskIds
              AND (:projectId IS NULL OR project.id <> :projectId)
            """)
    boolean hasTasksAssignedToAnotherProject(@Param("taskIds") Collection<Long> taskIds,
                                            @Param("projectId") Long projectId);
}
