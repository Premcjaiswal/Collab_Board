package com.collabboard.evaluation;

import com.collabboard.assignment.AssignmentDtos.ClassRef;
import com.collabboard.evaluation.RubricService.RubricCriterionDto;
import com.collabboard.submission.SubmissionDtos.EvaluationDto;
import com.collabboard.submission.SubmissionDtos.VersionDto;
import com.collabboard.submission.SubmissionStatus;
import com.collabboard.team.TeamDtos.MemberDto;
import com.collabboard.user.ProfileDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    public enum ItemKind { ASSIGNMENT, PROJECT }

    public enum OwnerType { STUDENT, TEAM }

    public record ScoreInput(@NotNull Long criterionId, @NotNull BigDecimal score) {
    }

    /**
     * marks is used when the item has no rubric; scores when it does. Marks are ignored for
     * REVISION_REQUESTED and REJECTED decisions. REJECTED requires a reason; REVISION_REQUESTED requires feedback.
     */
    public record EvaluateRequest(
            @NotNull(message = "Choose a decision.") EvaluationDecision decision,
            BigDecimal marks,
            @Valid @Size(max = 25) List<ScoreInput> scores,
            @Size(max = 10000, message = "Feedback can be at most 10000 characters.") String feedback,
            @Size(max = 1000, message = "Reason can be at most 1000 characters.") String reason,
            boolean publish) {
    }

    public record CurrentEvaluation(Long id, EvaluationDecision decision, BigDecimal totalMarks, boolean published) {
    }

    public record SubmissionRow(Long submissionId, ItemKind kind, Long itemId, String itemTitle, Long classId,
                                String className, OwnerType ownerType, Long ownerId, String ownerName, String ownerDetail,
                                SubmissionStatus status, int versionCount, Instant lastSubmittedAt, boolean late,
                                CurrentEvaluation evaluation, int maxMarks, Instant deadline) {
    }

    public record ItemRef(Long id, ItemKind kind, String title, int maxMarks, Instant deadline,
                          boolean allowResubmission, boolean allowLate, boolean group) {
    }

    public record TeamRef(Long id, String name, List<MemberDto> members) {
    }

    public record SubmissionDetail(Long submissionId, ItemRef item, ClassRef classroom, OwnerType ownerType,
                                   UserSummary student, TeamRef team, SubmissionStatus status,
                                   List<RubricCriterionDto> rubric, List<VersionDto> versions,
                                   List<EvaluationDto> evaluations) {
    }
}
