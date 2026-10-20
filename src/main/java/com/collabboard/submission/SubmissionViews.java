package com.collabboard.submission;

import com.collabboard.evaluation.Evaluation;
import com.collabboard.evaluation.EvaluationRepository;
import com.collabboard.storage.FileDto;
import com.collabboard.storage.StoredFile;
import com.collabboard.submission.SubmissionDtos.EvaluationDto;
import com.collabboard.submission.SubmissionDtos.ScoreDto;
import com.collabboard.submission.SubmissionDtos.SubmissionSummary;
import com.collabboard.submission.SubmissionDtos.SubmissionView;
import com.collabboard.submission.SubmissionDtos.VersionDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Maps submission entities to DTOs. Callers are responsible for authorization. */
@Component
public class SubmissionViews {

    private final SubmissionVersionRepository versions;
    private final EvaluationRepository evaluations;

    public SubmissionViews(SubmissionVersionRepository versions, EvaluationRepository evaluations) {
        this.versions = versions;
        this.evaluations = evaluations;
    }

    public SubmissionSummary summary(Submission s, WorkItem item) {
        if (s == null) {
            return new SubmissionSummary(null, SubmissionStatus.NOT_SUBMITTED, 0, null, false, null, null, item.getMaxMarks());
        }
        Evaluation published = evaluations.findFirstBySubmissionIdAndPublishedTrueOrderByPublishedAtDescIdDesc(s.getId()).orElse(null);
        boolean late = s.getCurrentVersion() > 0 && versions.findBySubmissionIdAndVersionNo(s.getId(), s.getCurrentVersion())
                .map(SubmissionVersion::isLate).orElse(false);
        return new SubmissionSummary(s.getId(), s.getStatus(), s.getCurrentVersion(), s.getLastSubmittedAt(), late,
                published == null ? null : published.getDecision(),
                published == null ? null : published.getTotalMarks(), item.getMaxMarks());
    }

    public List<VersionDto> versions(Submission s) {
        return versions.findBySubmissionIdOrderByVersionNoDesc(s.getId()).stream().map(this::version).toList();
    }

    public VersionDto version(SubmissionVersion v) {
        List<FileDto> files = v.getFiles().stream()
                .sorted(Comparator.comparing(StoredFile::getId))
                .map(FileDto::of).toList();
        return new VersionDto(v.getId(), v.getVersionNo(), v.getSubmittedBy().getFullName(), v.getSubmittedAt(),
                v.isLate(), v.getGithubUrl(), v.getNotes(), files);
    }

    public List<EvaluationDto> evaluations(Submission s, boolean includeUnpublished) {
        List<Evaluation> list = includeUnpublished
                ? evaluations.findBySubmissionIdOrderByCreatedAtDescIdDesc(s.getId())
                : evaluations.findBySubmissionIdAndPublishedTrueOrderByPublishedAtDescIdDesc(s.getId());
        int max = s.item().getMaxMarks();
        return list.stream().map(e -> evaluation(e, max)).toList();
    }

    public EvaluationDto evaluation(Evaluation e, int maxMarks) {
        List<ScoreDto> scores = e.getScores().stream()
                .map(sc -> new ScoreDto(sc.getCriterion().getId(), sc.getCriterion().getTitle(),
                        sc.getCriterion().getMaxMarks(), sc.getScore()))
                .toList();
        return new EvaluationDto(e.getId(), e.getDecision(), e.getTotalMarks(), maxMarks, e.getFeedback(), e.getReason(),
                e.isPublished(), e.getPublishedAt(), e.getCreatedAt(), e.getEvaluator().getFullName(),
                e.getVersion() == null ? null : e.getVersion().getVersionNo(), e.isCurrent(), scores);
    }

    /** Student-facing view: versions plus published evaluations only. */
    public SubmissionView studentView(WorkItem item, Submission s) {
        Instant now = Instant.now();
        SubmissionStatus status = s == null ? SubmissionStatus.NOT_SUBMITTED : s.getStatus();
        int count = s == null ? 0 : s.getCurrentVersion();
        SubmissionPolicy.Decision d = SubmissionPolicy.evaluate(item, status, count, now);
        return new SubmissionView(s == null ? null : s.getId(), status, item.getDeadline(), now.isAfter(item.getDeadline()),
                d.allowed(), d.late(), d.reason(),
                s == null ? List.of() : versions(s),
                s == null ? List.of() : evaluations(s, false));
    }
}
