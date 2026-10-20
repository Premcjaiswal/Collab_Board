package com.collabboard.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByClassroomIdOrderByDeadlineAsc(Long classroomId);

    List<Project> findByClassroomIdInOrderByDeadlineAsc(Collection<Long> classroomIds);

    long countByClassroomId(Long classroomId);

    @Query("select p from Project p where p.deadline > :from and p.deadline <= :to")
    List<Project> findDueBetween(@Param("from") Instant from, @Param("to") Instant to);
}
