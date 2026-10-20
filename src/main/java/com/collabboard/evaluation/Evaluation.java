package com.collabboard.evaluation;

import com.collabboard.submission.Submission;
import com.collabboard.submission.SubmissionVersion;
import com.collabboard.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Append-only evaluation record. Updating an evaluation creates a new row; old rows keep is_current=false. */
@Entity
@Table(name = "evaluations")
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private SubmissionVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluator_id")
    private User evaluator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvaluationDecision decision;

    @Column(name = "total_marks", precision = 7, scale = 2)
    private BigDecimal totalMarks;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(length = 1000)
    private String reason;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<EvaluationScore> scores = new ArrayList<>();

    protected Evaluation() {
    }

    public Evaluation(Submission submission, SubmissionVersion version, User evaluator, EvaluationDecision decision,
                      BigDecimal totalMarks, String feedback, String reason) {
        this.submission = submission;
        this.version = version;
        this.evaluator = evaluator;
        this.decision = decision;
        this.totalMarks = totalMarks;
        this.feedback = feedback;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public void addScore(RubricCriterion criterion, BigDecimal score) {
        scores.add(new EvaluationScore(this, criterion, score));
    }

    public void publish() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Submission getSubmission() {
        return submission;
    }

    public SubmissionVersion getVersion() {
        return version;
    }

    public User getEvaluator() {
        return evaluator;
    }

    public EvaluationDecision getDecision() {
        return decision;
    }

    public BigDecimal getTotalMarks() {
        return totalMarks;
    }

    public String getFeedback() {
        return feedback;
    }

    public String getReason() {
        return reason;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<EvaluationScore> getScores() {
        return scores;
    }
}
