package com.collabboard.evaluation;

import com.collabboard.assignment.Assignment;
import com.collabboard.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A teacher-defined rubric line for one specific assignment or project. */
@Entity
@Table(name = "rubric_criteria")
public class RubricCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "max_marks", nullable = false)
    private int maxMarks;

    @Column(nullable = false)
    private int position;

    protected RubricCriterion() {
    }

    public RubricCriterion(Assignment assignment, Project project, String title, String description, int maxMarks, int position) {
        this.assignment = assignment;
        this.project = project;
        this.title = title;
        this.description = description;
        this.maxMarks = maxMarks;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxMarks() {
        return maxMarks;
    }

    public int getPosition() {
        return position;
    }
}
