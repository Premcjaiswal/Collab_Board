package com.collabboard.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, Long> {

    List<RubricCriterion> findByAssignmentIdOrderByPositionAsc(Long assignmentId);

    List<RubricCriterion> findByProjectIdOrderByPositionAsc(Long projectId);

    void deleteByAssignmentId(Long assignmentId);

    void deleteByProjectId(Long projectId);
}
