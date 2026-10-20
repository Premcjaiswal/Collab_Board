package com.collabboard.project;

import com.collabboard.classroom.Classroom;
import com.collabboard.submission.WorkItem;
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

@Entity
@Table(name = "projects")
public class Project implements WorkItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "max_marks", nullable = false)
    private int maxMarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_mode", nullable = false, length = 20)
    private ParticipationMode participationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_formation", length = 30)
    private TeamFormation teamFormation;

    @Column(name = "max_team_size")
    private Integer maxTeamSize;

    @Column(name = "allowed_extensions", nullable = false, length = 255)
    private String allowedExtensions;

    @Column(name = "max_file_size_mb", nullable = false)
    private int maxFileSizeMb;

    @Column(name = "allow_resubmission", nullable = false)
    private boolean allowResubmission;

    @Column(name = "allow_late", nullable = false)
    private boolean allowLate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isGroup() {
        return participationMode == ParticipationMode.GROUP;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public Classroom getClassroom() {
        return classroom;
    }

    public void setClassroom(Classroom classroom) {
        this.classroom = classroom;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Instant getDeadline() {
        return deadline;
    }

    public void setDeadline(Instant deadline) {
        this.deadline = deadline;
    }

    @Override
    public int getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(int maxMarks) {
        this.maxMarks = maxMarks;
    }

    public ParticipationMode getParticipationMode() {
        return participationMode;
    }

    public void setParticipationMode(ParticipationMode participationMode) {
        this.participationMode = participationMode;
    }

    public TeamFormation getTeamFormation() {
        return teamFormation;
    }

    public void setTeamFormation(TeamFormation teamFormation) {
        this.teamFormation = teamFormation;
    }

    public Integer getMaxTeamSize() {
        return maxTeamSize;
    }

    public void setMaxTeamSize(Integer maxTeamSize) {
        this.maxTeamSize = maxTeamSize;
    }

    @Override
    public String getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(String allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    @Override
    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    @Override
    public boolean isAllowResubmission() {
        return allowResubmission;
    }

    public void setAllowResubmission(boolean allowResubmission) {
        this.allowResubmission = allowResubmission;
    }

    @Override
    public boolean isAllowLate() {
        return allowLate;
    }

    public void setAllowLate(boolean allowLate) {
        this.allowLate = allowLate;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
