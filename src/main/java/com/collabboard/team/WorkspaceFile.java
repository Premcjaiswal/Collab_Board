package com.collabboard.team;

import com.collabboard.storage.StoredFile;
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
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A file in a group workspace. Replacing or deleting never removes the row or the bytes:
 * old versions stay as REPLACED/DELETED history and deleted files can be restored.
 */
@Entity
@Table(name = "workspace_files")
public class WorkspaceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id")
    private StoredFile file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkspaceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceFileStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_id")
    private WorkspaceFile replaces;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by")
    private User removedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected WorkspaceFile() {
    }

    public WorkspaceFile(Team team, StoredFile file, WorkspaceCategory category, WorkspaceFile replaces, User uploadedBy) {
        this.team = team;
        this.file = file;
        this.category = category;
        this.replaces = replaces;
        this.uploadedBy = uploadedBy;
        this.status = WorkspaceFileStatus.ACTIVE;
        this.uploadedAt = Instant.now();
    }

    public void markRemoved(WorkspaceFileStatus newStatus, User by) {
        this.status = newStatus;
        this.removedBy = by;
        this.removedAt = Instant.now();
    }

    public void restore() {
        this.status = WorkspaceFileStatus.ACTIVE;
        this.removedBy = null;
        this.removedAt = null;
    }

    public Long getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public StoredFile getFile() {
        return file;
    }

    public WorkspaceCategory getCategory() {
        return category;
    }

    public WorkspaceFileStatus getStatus() {
        return status;
    }

    public WorkspaceFile getReplaces() {
        return replaces;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public User getRemovedBy() {
        return removedBy;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }
}
