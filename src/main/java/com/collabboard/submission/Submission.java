package com.collabboard.submission;

import com.collabboard.assignment.Assignment;
import com.collabboard.project.Project;
import com.collabboard.team.Team;
import com.collabboard.user.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The submission record for one work item (assignment or project) and one owner (student or team).
 * Individual work has {@code student} set; group projects have {@code team} set.
 */
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubmissionStatus status;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "last_submitted_at")
    private Instant lastSubmittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Submission() {
    }

    public static Submission forAssignment(Assignment a, User student) {
        Submission s = new Submission();
        s.assignment = a;
        s.student = student;
        s.status = SubmissionStatus.NOT_SUBMITTED;
        return s;
    }

    public static Submission forIndividualProject(Project p, User student) {
        Submission s = new Submission();
        s.project = p;
        s.student = student;
        s.status = SubmissionStatus.NOT_SUBMITTED;
        return s;
    }

    public static Submission forTeam(Project p, Team team) {
        Submission s = new Submission();
        s.project = p;
        s.team = team;
        s.status = SubmissionStatus.NOT_SUBMITTED;
        return s;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public WorkItem item() {
        return assignment != null ? assignment : project;
    }

    public boolean isAssignment() {
        return assignment != null;
    }

    public Long getId() {
        return id;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public Project getProject() {
        return project;
    }

    public User getStudent() {
        return student;
    }

    public Team getTeam() {
        return team;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public Instant getLastSubmittedAt() {
        return lastSubmittedAt;
    }

    public void setLastSubmittedAt(Instant lastSubmittedAt) {
        this.lastSubmittedAt = lastSubmittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
