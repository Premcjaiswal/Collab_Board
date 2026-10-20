package com.collabboard.team;

import com.collabboard.storage.FileDto;
import com.collabboard.submission.SubmissionDtos.SubmissionSummary;
import com.collabboard.submission.SubmissionDtos.SubmissionView;
import com.collabboard.user.ProfileDtos.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TeamDtos {

    private TeamDtos() {
    }

    /** studentIds is only honoured for teachers creating teams for students. */
    public record CreateTeamRequest(
            @NotBlank(message = "Team name is required.") @Size(max = 100) String name,
            @Size(max = 20) List<Long> studentIds) {
    }

    public record InviteRequest(
            @NotBlank(message = "Enter the classmate's college email.") @Email(message = "Enter a valid email address.") String email) {
    }

    public record MemberRequest(@NotNull(message = "Choose a student.") Long studentId) {
    }

    public record JoinTeamRequest(@NotBlank(message = "Enter the team code.") @Size(max = 20) String code) {
    }

    public record DetailsRequest(@Size(max = 300) String githubUrl, @Size(max = 20000) String notes) {
    }

    public record MemberDto(Long studentId, String fullName, String email, String rollNumber, Instant joinedAt) {
    }

    public record InvitationDto(Long id, Long teamId, String teamName, Long projectId, String projectTitle,
                                String className, UserSummary invitee, String invitedBy, InvitationStatus status,
                                Instant createdAt, Instant respondedAt) {
    }

    /** joinCode is set for student-created teams; TeamDto is only returned to team members and the class teacher. */
    public record TeamDto(Long id, String name, Long projectId, String projectTitle, int maxTeamSize,
                          List<MemberDto> members, List<InvitationDto> pendingInvitations, int slotsLeft,
                          SubmissionSummary submission, Instant createdAt, String joinCode) {
    }

    public record WorkspaceFileDto(Long id, WorkspaceCategory category, WorkspaceFileStatus status, FileDto file,
                                   String uploadedBy, Instant uploadedAt, String removedBy, Instant removedAt,
                                   Long replacesId) {
    }

    public record ActivityDto(Long id, String actor, String action, String details, Instant createdAt) {
    }

    public record WorkspaceView(TeamDto team, boolean canEdit, String githubUrl, String notes, String notesUpdatedBy,
                                Instant notesUpdatedAt, List<WorkspaceFileDto> activeFiles,
                                List<WorkspaceFileDto> history, List<ActivityDto> activity, SubmissionView submission,
                                List<String> allowedExtensions, int maxFileSizeMb) {
    }

    public record MyTeamResponse(TeamDto team, List<InvitationDto> invitations) {
    }
}
