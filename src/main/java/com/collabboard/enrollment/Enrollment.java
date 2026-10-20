package com.collabboard.enrollment;

import com.collabboard.classroom.Classroom;
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
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected Enrollment() {
    }

    public Enrollment(Classroom classroom, User student) {
        this.classroom = classroom;
        this.student = student;
        this.joinedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Classroom getClassroom() {
        return classroom;
    }

    public User getStudent() {
        return student;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
