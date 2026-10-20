package com.collabboard.submission;

import com.collabboard.storage.StoredFile;
import com.collabboard.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable snapshot of one (re)submission: who submitted, when, and exactly which files. */
@Entity
@Table(name = "submission_versions")
public class SubmissionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(nullable = false)
    private boolean late;

    @Column(name = "github_url", length = 300)
    private String githubUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "submission_version_files",
            joinColumns = @JoinColumn(name = "version_id"),
            inverseJoinColumns = @JoinColumn(name = "file_id"))
    private Set<StoredFile> files = new LinkedHashSet<>();

    protected SubmissionVersion() {
    }

    public SubmissionVersion(Submission submission, int versionNo, User submittedBy, boolean late,
                             String githubUrl, String notes) {
        this.submission = submission;
        this.versionNo = versionNo;
        this.submittedBy = submittedBy;
        this.submittedAt = Instant.now();
        this.late = late;
        this.githubUrl = githubUrl;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public Submission getSubmission() {
        return submission;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public boolean isLate() {
        return late;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public String getNotes() {
        return notes;
    }

    public Set<StoredFile> getFiles() {
        return files;
    }
}
