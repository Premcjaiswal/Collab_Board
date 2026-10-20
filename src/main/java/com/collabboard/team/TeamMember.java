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

@Entity
@Table(name = "team_members")
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    /** Denormalised so a unique key guarantees one team per student per project. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected TeamMember() {
    }

    public TeamMember(Team team, User student) {
        this.team = team;
        this.project = team.getProject();
        this.student = student;
        this.joinedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public Project getProject() {
        return project;
    }

    public User getStudent() {
        return student;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
