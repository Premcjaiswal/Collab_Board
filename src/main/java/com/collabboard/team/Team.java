package com.collabboard.team;

import com.collabboard.project.Project;
import com.collabboard.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A group-project team. There is no leader: every current member has identical permissions. */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 100)
    private String name;

    /** Shared by members of student-created teams so classmates can join; null for teacher-assigned teams. */
    @Column(name = "join_code", length = 12, unique = true)
    private String joinCode;

    @Column(name = "github_url", length = 300)
    private String githubUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notes_updated_by")
    private User notesUpdatedBy;

    @Column(name = "notes_updated_at")
    private Instant notesUpdatedAt;

    /** Audit only — confers no extra permissions. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Team() {
    }

    public Team(Project project, String name, User createdBy) {
        this.project = project;
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getJoinCode() {
        return joinCode;
    }

    public void setJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public User getNotesUpdatedBy() {
        return notesUpdatedBy;
    }

    public void setNotesUpdatedBy(User notesUpdatedBy) {
        this.notesUpdatedBy = notesUpdatedBy;
    }

    public Instant getNotesUpdatedAt() {
        return notesUpdatedAt;
    }

    public void setNotesUpdatedAt(Instant notesUpdatedAt) {
        this.notesUpdatedAt = notesUpdatedAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
