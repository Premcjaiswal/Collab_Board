package com.collabboard.evaluation;

import com.collabboard.assignment.Assignment;
import com.collabboard.common.ApiException;
import com.collabboard.common.Texts;
import com.collabboard.project.Project;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Per-assignment / per-project rubric definitions. There is no global rubric. */
@Service
public class RubricService {

    public record RubricItem(
            @NotBlank(message = "Criterion title is required.") @Size(max = 150) String title,
            @Size(max = 500) String description,
            @Min(value = 1, message = "Criterion marks must be at least 1.") @Max(1000) int maxMarks) {
    }

    public record RubricCriterionDto(Long id, String title, String description, int maxMarks, int position) {
        static RubricCriterionDto of(RubricCriterion c) {
            return new RubricCriterionDto(c.getId(), c.getTitle(), c.getDescription(), c.getMaxMarks(), c.getPosition());
        }
    }

    private final RubricCriterionRepository criteria;
    private final EvaluationRepository evaluations;

    public RubricService(RubricCriterionRepository criteria, EvaluationRepository evaluations) {
        this.criteria = criteria;
        this.evaluations = evaluations;
    }

    public static void validate(List<RubricItem> items, int maxMarks) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (items.size() > 25) {
            throw ApiException.badRequest("RUBRIC_INVALID", "A rubric can have at most 25 criteria.");
        }
        int sum = items.stream().mapToInt(RubricItem::maxMarks).sum();
        if (sum != maxMarks) {
            throw ApiException.badRequest("RUBRIC_INVALID",
                    "Rubric criteria must add up to the maximum marks (" + maxMarks + "); they currently add up to " + sum + ".");
        }
    }

    @Transactional(readOnly = true)
    public List<RubricCriterionDto> forAssignment(Long assignmentId) {
        return criteria.findByAssignmentIdOrderByPositionAsc(assignmentId).stream().map(RubricCriterionDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<RubricCriterionDto> forProject(Long projectId) {
        return criteria.findByProjectIdOrderByPositionAsc(projectId).stream().map(RubricCriterionDto::of).toList();
    }

    /** Marks already awarded are relative to the current maximum, so it is fixed once evaluations exist. */
    public void requireMaxMarksUnchanged(Assignment a, int newMaxMarks) {
        if (a.getMaxMarks() != newMaxMarks && evaluations.existsBySubmissionAssignmentId(a.getId())) {
            throw marksLocked();
        }
    }

    public void requireMaxMarksUnchanged(Project p, int newMaxMarks) {
        if (p.getMaxMarks() != newMaxMarks && evaluations.existsBySubmissionProjectId(p.getId())) {
            throw marksLocked();
        }
    }

    private static ApiException marksLocked() {
        return ApiException.conflict("MARKS_LOCKED", "Maximum marks cannot be changed after evaluations have been recorded.");
    }

    @Transactional
    public void replaceForAssignment(Assignment a, List<RubricItem> items) {
        List<RubricCriterion> existing = criteria.findByAssignmentIdOrderByPositionAsc(a.getId());
        if (sameRubric(existing, items)) {
            return;
        }
        if (evaluations.existsBySubmissionAssignmentId(a.getId())) {
            throw ApiException.conflict("RUBRIC_LOCKED", "The rubric cannot be changed after evaluations have been recorded.");
        }
        criteria.deleteAll(existing);
        criteria.flush();
        criteria.saveAll(build(a, null, items));
    }

    @Transactional
    public void replaceForProject(Project p, List<RubricItem> items) {
        List<RubricCriterion> existing = criteria.findByProjectIdOrderByPositionAsc(p.getId());
        if (sameRubric(existing, items)) {
            return;
        }
        if (evaluations.existsBySubmissionProjectId(p.getId())) {
            throw ApiException.conflict("RUBRIC_LOCKED", "The rubric cannot be changed after evaluations have been recorded.");
        }
        criteria.deleteAll(existing);
        criteria.flush();
        criteria.saveAll(build(null, p, items));
    }

    private static List<RubricCriterion> build(Assignment a, Project p, List<RubricItem> items) {
        List<RubricCriterion> list = new ArrayList<>();
        if (items == null) {
            return list;
        }
        int pos = 0;
        for (RubricItem i : items) {
            list.add(new RubricCriterion(a, p, i.title().trim(), Texts.trimToNull(i.description()), i.maxMarks(), pos++));
        }
        return list;
    }

    private static boolean sameRubric(List<RubricCriterion> existing, List<RubricItem> items) {
        List<RubricItem> wanted = items == null ? List.of() : items;
        if (existing.size() != wanted.size()) {
            return false;
        }
        for (int i = 0; i < existing.size(); i++) {
            RubricCriterion e = existing.get(i);
            RubricItem w = wanted.get(i);
            if (!e.getTitle().equals(w.title().trim()) || e.getMaxMarks() != w.maxMarks()
                    || !Objects.equals(e.getDescription(), Texts.trimToNull(w.description()))) {
                return false;
            }
        }
        return true;
    }
}
