package com.collabboard.project;

import com.collabboard.assignment.AssignmentDtos.ClassRef;
import com.collabboard.assignment.AssignmentDtos.ItemStats;
import com.collabboard.evaluation.RubricService.RubricCriterionDto;
import com.collabboard.evaluation.RubricService.RubricItem;
import com.collabboard.submission.SubmissionDtos.SubmissionSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotNull(message = "Choose a class.") Long classId,
            @NotBlank(message = "Title is required.") @Size(max = 200) String title,
            @NotBlank(message = "Description and requirements are required.") @Size(max = 20000) String description,
            @NotNull(message = "Deadline is required.") Instant deadline,
            @NotNull(message = "Maximum marks are required.") @Min(value = 1, message = "Maximum marks must be at least 1.")
            @Max(value = 1000, message = "Maximum marks cannot exceed 1000.") Integer maxMarks,
            @NotNull(message = "Choose individual or group participation.") ParticipationMode participationMode,
            TeamFormation teamFormation,
            @Min(value = 2, message = "A team needs at least 2 members.") @Max(value = 20, message = "Teams can have at most 20 members.") Integer maxTeamSize,
            @NotBlank(message = "List the permitted file types.") @Size(max = 255) String allowedExtensions,
            @NotNull(message = "Maximum file size is required.") @Min(value = 1, message = "Minimum size is 1 MB.")
            @Max(value = 50, message = "Maximum size is 50 MB.") Integer maxFileSizeMb,
            boolean allowResubmission,
            boolean allowLate,
            @Valid @Size(max = 25) List<RubricItem> rubric) {
    }

    public record MyTeamRef(Long teamId, String name, int memberCount) {
    }

    /**
     * For students: mySubmission is the individual submission (individual projects) or the team
     * submission (group projects), and myTeam/pendingInvitations describe their team state.
     */
    public record ProjectView(Long id, ClassRef classroom, String title, String description, Instant deadline,
                              boolean pastDeadline, int maxMarks, ParticipationMode participationMode,
                              TeamFormation teamFormation, Integer maxTeamSize, List<String> allowedExtensions,
                              int maxFileSizeMb, boolean allowResubmission, boolean allowLate,
                              List<RubricCriterionDto> rubric, Instant createdAt, SubmissionSummary mySubmission,
                              MyTeamRef myTeam, int pendingInvitations, ItemStats stats, long teamCount) {
    }
}
