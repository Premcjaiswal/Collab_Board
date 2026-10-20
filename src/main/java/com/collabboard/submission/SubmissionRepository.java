package com.collabboard.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    Optional<Submission> findByProjectIdAndStudentId(Long projectId, Long studentId);

    Optional<Submission> findByProjectIdAndTeamId(Long projectId, Long teamId);

    Optional<Submission> findByTeamId(Long teamId);

    List<Submission> findByAssignmentId(Long assignmentId);

    List<Submission> findByProjectId(Long projectId);

    List<Submission> findByStudentId(Long studentId);

    List<Submission> findByTeamIdIn(Collection<Long> teamIds);

    boolean existsByAssignmentIdAndCurrentVersionGreaterThan(Long assignmentId, int version);

    boolean existsByProjectIdAndCurrentVersionGreaterThan(Long projectId, int version);

    boolean existsByAssignmentId(Long assignmentId);

    boolean existsByProjectId(Long projectId);

    /** Submissions in classes taught by the teacher (any work item type). */
    @Query("""
            select s from Submission s
              left join s.assignment a
              left join a.classroom ac
              left join s.project p
              left join p.classroom pc
            where (ac.teacher.id = :teacherId or pc.teacher.id = :teacherId)
              and s.currentVersion > 0
            order by s.lastSubmittedAt desc
            """)
    List<Submission> findSubmittedForTeacher(@Param("teacherId") Long teacherId);
}
