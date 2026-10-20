package com.collabboard.submission;

import com.collabboard.evaluation.EvaluationDecision;
import com.collabboard.storage.FileDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SubmissionDtos {

    private SubmissionDtos() {
    }

    /** Compact status used in lists. Marks are present only when an evaluation has been published. */
    public record SubmissionSummary(Long submissionId, SubmissionStatus status, int versionCount,
                                    Instant lastSubmittedAt, boolean late, EvaluationDecision publishedDecision,
                                    BigDecimal publishedMarks, int maxMarks) {
    }

    public record VersionDto(Long id, int versionNo, String submittedBy, Instant submittedAt, boolean late,
                             String githubUrl, String notes, List<FileDto> files) {
    }

    public record ScoreDto(Long criterionId, String criterion, int maxMarks, BigDecimal score) {
    }

    public record EvaluationDto(Long id, EvaluationDecision decision, BigDecimal totalMarks, int maxMarks,
                                String feedback, String reason, boolean published, Instant publishedAt,
                                Instant createdAt, String evaluator, Integer versionNo, boolean current,
                                List<ScoreDto> scores) {
    }

    /** What a student (or team member) sees for their own work. Only published evaluations are included. */
    public record SubmissionView(Long submissionId, SubmissionStatus status, Instant deadline, boolean pastDeadline,
                                 boolean canSubmit, boolean nextSubmissionLate, String blockedReason,
                                 List<VersionDto> versions, List<EvaluationDto> evaluations) {
    }
}
