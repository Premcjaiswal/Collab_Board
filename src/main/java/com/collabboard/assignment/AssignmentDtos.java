package com.collabboard.assignment;

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

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    public record AssignmentRequest(
            @NotNull(message = "Choose a class.") Long classId,
            @NotBlank(message = "Title is required.") @Size(max = 200) String title,
            @NotBlank(message = "Instructions are required.") @Size(max = 20000) String description,
            @NotNull(message = "Choose a category.") AssignmentCategory category,
            @NotNull(message = "Deadline is required.") Instant deadline,
            @NotNull(message = "Maximum marks are required.") @Min(value = 1, message = "Maximum marks must be at least 1.")
            @Max(value = 1000, message = "Maximum marks cannot exceed 1000.") Integer maxMarks,
            @NotBlank(message = "List the permitted file types.") @Size(max = 255) String allowedExtensions,
            @NotNull(message = "Maximum file size is required.") @Min(value = 1, message = "Minimum size is 1 MB.")
            @Max(value = 50, message = "Maximum size is 50 MB.") Integer maxFileSizeMb,
            boolean allowResubmission,
            boolean allowLate,
            @Valid @Size(max = 25) List<RubricItem> rubric) {
    }

    public record ClassRef(Long id, String name, String displayName, String subjectName) {
    }

    /**
     * mySubmission is filled for students; stats for the class teacher.
     */
    public record AssignmentView(Long id, ClassRef classroom, String title, String description,
                                 AssignmentCategory category, Instant deadline, boolean pastDeadline, int maxMarks,
                                 List<String> allowedExtensions, int maxFileSizeMb, boolean allowResubmission,
                                 boolean allowLate, List<RubricCriterionDto> rubric, Instant createdAt,
                                 SubmissionSummary mySubmission, ItemStats stats) {
    }

    public record ItemStats(long expected, long submitted, long evaluated, long pendingEvaluation) {
    }
}
