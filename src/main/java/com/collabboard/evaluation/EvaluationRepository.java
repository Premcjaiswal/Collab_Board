package com.collabboard.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    List<Evaluation> findBySubmissionIdOrderByCreatedAtDescIdDesc(Long submissionId);

    Optional<Evaluation> findFirstBySubmissionIdAndCurrentTrue(Long submissionId);

    Optional<Evaluation> findFirstBySubmissionIdAndPublishedTrueOrderByPublishedAtDescIdDesc(Long submissionId);

    List<Evaluation> findBySubmissionIdAndPublishedTrueOrderByPublishedAtDescIdDesc(Long submissionId);

    List<Evaluation> findBySubmissionIdInAndCurrentTrue(Collection<Long> submissionIds);

    boolean existsBySubmissionAssignmentId(Long assignmentId);

    boolean existsBySubmissionProjectId(Long projectId);
}
