package com.collabboard.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByClassroomIdOrderByDeadlineAsc(Long classroomId);

    List<Assignment> findByClassroomIdInOrderByDeadlineAsc(Collection<Long> classroomIds);

    long countByClassroomId(Long classroomId);

    @Query("select a from Assignment a where a.deadline > :from and a.deadline <= :to")
    List<Assignment> findDueBetween(@Param("from") Instant from, @Param("to") Instant to);
}
