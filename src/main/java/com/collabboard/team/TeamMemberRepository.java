package com.collabboard.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId);

    List<TeamMember> findByTeamIdIn(Collection<Long> teamIds);

    Optional<TeamMember> findByProjectIdAndStudentId(Long projectId, Long studentId);

    Optional<TeamMember> findByTeamIdAndStudentId(Long teamId, Long studentId);

    boolean existsByTeamIdAndStudentId(Long teamId, Long studentId);

    long countByTeamId(Long teamId);

    List<TeamMember> findByProjectId(Long projectId);

    List<TeamMember> findByStudentId(Long studentId);

    @Query("select m from TeamMember m where m.student.id = :studentId and m.project.classroom.id = :classId")
    List<TeamMember> findByStudentInClass(@Param("studentId") Long studentId, @Param("classId") Long classId);
}
