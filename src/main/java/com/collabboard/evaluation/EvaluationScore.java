package com.collabboard.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "evaluation_scores")
public class EvaluationScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id")
    private Evaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id")
    private RubricCriterion criterion;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal score;

    protected EvaluationScore() {
    }

    EvaluationScore(Evaluation evaluation, RubricCriterion criterion, BigDecimal score) {
        this.evaluation = evaluation;
        this.criterion = criterion;
        this.score = score;
    }

    public Long getId() {
        return id;
    }

    public RubricCriterion getCriterion() {
        return criterion;
    }

    public BigDecimal getScore() {
        return score;
    }
}
