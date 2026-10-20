package com.collabboard.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {

    List<TeamInvitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(Long inviteeId, InvitationStatus status);

    List<TeamInvitation> findByTeamIdAndStatus(Long teamId, InvitationStatus status);

    List<TeamInvitation> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    List<TeamInvitation> findByProjectIdAndInviteeIdAndStatus(Long projectId, Long inviteeId, InvitationStatus status);

    long countByTeamIdAndStatus(Long teamId, InvitationStatus status);

    boolean existsByTeamIdAndInviteeIdAndStatus(Long teamId, Long inviteeId, InvitationStatus status);

    @Query("select i from TeamInvitation i where i.invitee.id = :studentId and i.status = :status and i.project.classroom.id = :classId")
    List<TeamInvitation> findByInviteeInClass(@Param("studentId") Long studentId, @Param("classId") Long classId,
                                              @Param("status") InvitationStatus status);
}
