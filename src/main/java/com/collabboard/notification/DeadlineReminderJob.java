package com.collabboard.notification;

import com.collabboard.assignment.Assignment;
import com.collabboard.assignment.AssignmentRepository;
import com.collabboard.common.PageRefs;
import com.collabboard.enrollment.EnrollmentRepository;
import com.collabboard.project.Project;
import com.collabboard.project.ProjectRepository;
import com.collabboard.submission.SubmissionRepository;
import com.collabboard.team.TeamMember;
import com.collabboard.team.TeamMemberRepository;
import com.collabboard.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates one "deadline in the next 24 hours" reminder per student and work item for students who
 * have not submitted yet. Reminders are de-duplicated with a unique key, so re-runs are harmless.
 */
@Component
public class DeadlineReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderJob.class);

    private final AssignmentRepository assignments;
    private final ProjectRepository projects;
    private final EnrollmentRepository enrollments;
    private final SubmissionRepository submissions;
    private final TeamMemberRepository members;
    private final NotificationService notifications;

    public DeadlineReminderJob(AssignmentRepository assignments, ProjectRepository projects,
                               EnrollmentRepository enrollments, SubmissionRepository submissions,
                               TeamMemberRepository members, NotificationService notifications) {
        this.assignments = assignments;
        this.projects = projects;
        this.enrollments = enrollments;
        this.submissions = submissions;
        this.members = members;
        this.notifications = notifications;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT30M")
    @Transactional
    public void run() {
        int created = sendReminders(Instant.now());
        if (created > 0) {
            log.info("Created {} deadline reminder notification(s).", created);
        }
    }

    @Transactional
    public int sendReminders(Instant now) {
        Instant until = now.plus(Duration.ofHours(24));
        int created = 0;
        for (Assignment a : assignments.findDueBetween(now, until)) {
            if (a.getClassroom().isArchived()) {
                continue; // archived classes accept no submissions, so a reminder would be misleading
            }
            for (User s : enrollments.findStudents(a.getClassroom().getId())) {
                boolean submitted = submissions.findByAssignmentIdAndStudentId(a.getId(), s.getId())
                        .map(x -> x.getCurrentVersion() > 0).orElse(false);
                if (!submitted && notifications.notifyOnce(s, "deadline24:A:" + a.getId() + ":" + s.getId(),
                        NotificationType.DEADLINE_REMINDER, "Due soon: " + a.getTitle(),
                        "This assignment for " + a.getClassroom().getName() + " is due within 24 hours and you haven't submitted yet.",
                        PageRefs.assignment(a.getId()))) {
                    created++;
                }
            }
        }
        for (Project p : projects.findDueBetween(now, until)) {
            if (p.getClassroom().isArchived()) {
                continue;
            }
            Map<Long, TeamMember> teamOf = p.isGroup()
                    ? members.findByProjectId(p.getId()).stream().collect(Collectors.toMap(m -> m.getStudent().getId(), Function.identity()))
                    : Map.of();
            for (User s : enrollments.findStudents(p.getClassroom().getId())) {
                boolean submitted;
                if (p.isGroup()) {
                    TeamMember m = teamOf.get(s.getId());
                    submitted = m != null && submissions.findByProjectIdAndTeamId(p.getId(), m.getTeam().getId())
                            .map(x -> x.getCurrentVersion() > 0).orElse(false);
                } else {
                    submitted = submissions.findByProjectIdAndStudentId(p.getId(), s.getId())
                            .map(x -> x.getCurrentVersion() > 0).orElse(false);
                }
                if (!submitted && notifications.notifyOnce(s, "deadline24:P:" + p.getId() + ":" + s.getId(),
                        NotificationType.DEADLINE_REMINDER, "Due soon: " + p.getTitle(),
                        "This project for " + p.getClassroom().getName() + " is due within 24 hours and "
                                + (p.isGroup() ? "your team hasn't" : "you haven't") + " submitted yet.",
                        PageRefs.project(p.getId()))) {
                    created++;
                }
            }
        }
        return created;
    }

}
