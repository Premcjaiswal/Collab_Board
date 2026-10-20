package com.collabboard.evaluation;

import com.collabboard.assignment.Assignment;
import com.collabboard.assignment.AssignmentDtos.ClassRef;
import com.collabboard.assignment.AssignmentRepository;
import com.collabboard.auth.AuthUser;
import com.collabboard.classroom.Classroom;
import com.collabboard.classroom.ClassroomAccess;
import com.collabboard.common.ApiException;
import com.collabboard.common.PageRefs;
import com.collabboard.common.Texts;
import com.collabboard.enrollment.EnrollmentRepository;
import com.collabboard.evaluation.EvaluationDtos.CurrentEvaluation;
import com.collabboard.evaluation.EvaluationDtos.EvaluateRequest;
import com.collabboard.evaluation.EvaluationDtos.ItemKind;
import com.collabboard.evaluation.EvaluationDtos.ItemRef;
import com.collabboard.evaluation.EvaluationDtos.OwnerType;
import com.collabboard.evaluation.EvaluationDtos.ScoreInput;
import com.collabboard.evaluation.EvaluationDtos.SubmissionDetail;
import com.collabboard.evaluation.EvaluationDtos.SubmissionRow;
import com.collabboard.evaluation.EvaluationDtos.TeamRef;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.project.Project;
import com.collabboard.project.ProjectRepository;
import com.collabboard.submission.Submission;
import com.collabboard.submission.SubmissionDtos.EvaluationDto;
import com.collabboard.submission.SubmissionRepository;
import com.collabboard.submission.SubmissionStatus;
import com.collabboard.submission.SubmissionVersion;
import com.collabboard.submission.SubmissionVersionRepository;
import com.collabboard.submission.SubmissionViews;
import com.collabboard.submission.WorkItem;
import com.collabboard.team.Team;
import com.collabboard.team.TeamMember;
import com.collabboard.team.TeamMemberRepository;
import com.collabboard.team.TeamRepository;
import com.collabboard.team.TeamService;
import com.collabboard.user.ProfileDtos.UserSummary;
import com.collabboard.user.ProfileService;
import com.collabboard.user.User;
import com.collabboard.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Teacher-side evaluation. Only the teacher of the class can list, inspect, evaluate or publish.
 * Evaluations are append-only; students only ever see published ones.
 */
@Service
public class EvaluationService {

    private final SubmissionRepository submissions;
    private final SubmissionVersionRepository versions;
    private final EvaluationRepository evaluations;
    private final RubricCriterionRepository criteria;
    private final RubricService rubricService;
    private final AssignmentRepository assignments;
    private final ProjectRepository projects;
    private final EnrollmentRepository enrollments;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TeamService teamService;
    private final UserRepository users;
    private final ProfileService profiles;
    private final ClassroomAccess access;
    private final SubmissionViews views;
    private final NotificationService notifications;

    public EvaluationService(SubmissionRepository submissions, SubmissionVersionRepository versions,
                             EvaluationRepository evaluations, RubricCriterionRepository criteria,
                             RubricService rubricService, AssignmentRepository assignments, ProjectRepository projects,
                             EnrollmentRepository enrollments, TeamRepository teams,
                             TeamMemberRepository members, TeamService teamService, UserRepository users,
                             ProfileService profiles, ClassroomAccess access, SubmissionViews views,
                             NotificationService notifications) {
        this.submissions = submissions;
        this.versions = versions;
        this.evaluations = evaluations;
        this.criteria = criteria;
        this.rubricService = rubricService;
        this.assignments = assignments;
        this.projects = projects;
        this.enrollments = enrollments;
        this.teams = teams;
        this.members = members;
        this.teamService = teamService;
        this.users = users;
        this.profiles = profiles;
        this.access = access;
        this.views = views;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ listing

    /**
     * Without itemId: every submitted record in the teacher's classes. With kind+itemId: a full roster
     * for that item including students/teams who have not submitted yet.
     *
     * @param status a SubmissionStatus name, or "PENDING" for SUBMITTED+LATE
     */
    @Transactional(readOnly = true)
    public List<SubmissionRow> list(AuthUser teacher, Long classId, ItemKind kind, Long itemId, String status) {
        if (classId != null) {
            access.requireOwned(classId, teacher);
        }
        List<SubmissionRow> rows;
        if (itemId != null) {
            if (kind == null) {
                throw ApiException.badRequest("Specify the kind (ASSIGNMENT or PROJECT) when filtering by item.");
            }
            rows = kind == ItemKind.ASSIGNMENT ? assignmentRoster(itemId, teacher) : projectRoster(itemId, teacher);
        } else {
            List<Submission> subs = submissions.findSubmittedForTeacher(teacher.id()).stream()
                    .filter(s -> classId == null || s.item().getClassroom().getId().equals(classId))
                    .filter(s -> kind == null || (kind == ItemKind.ASSIGNMENT) == s.isAssignment())
                    .toList();
            Map<Long, Evaluation> current = currentEvaluations(subs);
            Map<Long, UserSummary> students = profiles.summaryMap(subs.stream()
                    .map(Submission::getStudent).filter(java.util.Objects::nonNull).toList());
            rows = subs.stream().map(s -> row(s, s.item(), s.getStudent() != null ? students.get(s.getStudent().getId()) : null,
                    s.getTeam(), current.get(s.getId()))).collect(Collectors.toCollection(ArrayList::new));
        }
        if (status != null && !status.isBlank()) {
            rows = rows.stream().filter(r -> matchesStatus(r.status(), status)).toList();
        }
        return rows.stream().sorted(Comparator
                .comparingInt((SubmissionRow r) -> priority(r.status()))
                .thenComparing(SubmissionRow::lastSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SubmissionRow::ownerName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private List<SubmissionRow> assignmentRoster(Long assignmentId, AuthUser teacher) {
        Assignment a = assignments.findById(assignmentId).orElseThrow(() -> ApiException.notFound("Assignment"));
        access.requireOwned(a.getClassroom().getId(), teacher);
        List<Submission> subs = submissions.findByAssignmentId(assignmentId);
        return individualRoster(a, subs);
    }

    private List<SubmissionRow> projectRoster(Long projectId, AuthUser teacher) {
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        access.requireOwned(p.getClassroom().getId(), teacher);
        List<Submission> subs = submissions.findByProjectId(projectId);
        if (!p.isGroup()) {
            return individualRoster(p, subs);
        }
        Map<Long, Submission> byTeam = subs.stream().filter(s -> s.getTeam() != null)
                .collect(Collectors.toMap(s -> s.getTeam().getId(), Function.identity()));
        Map<Long, Evaluation> current = currentEvaluations(subs);
        return teams.findByProjectIdOrderByNameAsc(projectId).stream().map(t -> {
            Submission s = byTeam.get(t.getId());
            return row(s, p, null, t, s == null ? null : current.get(s.getId()));
        }).toList();
    }

    private List<SubmissionRow> individualRoster(WorkItem item, List<Submission> subs) {
        Map<Long, Submission> byStudent = subs.stream().filter(s -> s.getStudent() != null)
                .collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));
        Map<Long, Evaluation> current = currentEvaluations(subs);
        List<User> roster = new ArrayList<>(enrollments.findStudents(item.getClassroom().getId()));
        Set<Long> inRoster = roster.stream().map(User::getId).collect(Collectors.toCollection(HashSet::new));
        // Keep the work of students who have since left the class visible to the teacher.
        subs.stream().filter(s -> s.getStudent() != null && !inRoster.contains(s.getStudent().getId())
                && s.getCurrentVersion() > 0).forEach(s -> roster.add(s.getStudent()));
        Map<Long, UserSummary> summaries = profiles.summaryMap(roster);
        return roster.stream().map(u -> {
            Submission s = byStudent.get(u.getId());
            return row(s, item, summaries.get(u.getId()), null, s == null ? null : current.get(s.getId()));
        }).toList();
    }

    private SubmissionRow row(Submission s, WorkItem item, UserSummary student, Team team, Evaluation current) {
        Classroom c = item.getClassroom();
        boolean isAssignment = item instanceof Assignment;
        OwnerType ownerType = team != null ? OwnerType.TEAM : OwnerType.STUDENT;
        String ownerName;
        String ownerDetail;
        Long ownerId;
        if (team != null) {
            ownerId = team.getId();
            ownerName = team.getName();
            ownerDetail = teamService.currentMembers(team).stream().map(User::getFullName).collect(Collectors.joining(", "));
        } else {
            ownerId = student.id();
            ownerName = student.fullName();
            ownerDetail = student.rollNumber() == null ? student.email() : student.rollNumber();
        }
        boolean late = s != null && s.getCurrentVersion() > 0 && versions.findBySubmissionIdAndVersionNo(s.getId(), s.getCurrentVersion())
                .map(SubmissionVersion::isLate).orElse(false);
        return new SubmissionRow(s == null ? null : s.getId(), isAssignment ? ItemKind.ASSIGNMENT : ItemKind.PROJECT,
                item.getId(), item.getTitle(), c.getId(), c.displayName(), ownerType, ownerId, ownerName, ownerDetail,
                s == null ? SubmissionStatus.NOT_SUBMITTED : s.getStatus(), s == null ? 0 : s.getCurrentVersion(),
                s == null ? null : s.getLastSubmittedAt(), late,
                current == null ? null : new CurrentEvaluation(current.getId(), current.getDecision(), current.getTotalMarks(), current.isPublished()),
                item.getMaxMarks(), item.getDeadline());
    }

    // ------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public SubmissionDetail detail(Long submissionId, AuthUser teacher) {
        Submission s = requireOwnedSubmission(submissionId, teacher);
        WorkItem item = s.item();
        Classroom c = item.getClassroom();
        boolean isAssignment = s.isAssignment();
        ItemRef ref = new ItemRef(item.getId(), isAssignment ? ItemKind.ASSIGNMENT : ItemKind.PROJECT, item.getTitle(),
                item.getMaxMarks(), item.getDeadline(), item.isAllowResubmission(), item.isAllowLate(),
                !isAssignment && s.getProject().isGroup());
        TeamRef teamRef = null;
        if (s.getTeam() != null) {
            teamRef = new TeamRef(s.getTeam().getId(), s.getTeam().getName(), teamService.toDto(s.getTeam()).members());
        }
        return new SubmissionDetail(s.getId(), ref, new ClassRef(c.getId(), c.getName(), c.displayName(), c.getSubjectName()),
                s.getTeam() != null ? OwnerType.TEAM : OwnerType.STUDENT,
                s.getStudent() != null ? profiles.summary(s.getStudent()) : null, teamRef, s.getStatus(),
                isAssignment ? rubricService.forAssignment(item.getId()) : rubricService.forProject(item.getId()),
                views.versions(s), views.evaluations(s, true));
    }

    // ------------------------------------------------------------------ evaluate / publish

    @Transactional
    public EvaluationDto evaluate(Long submissionId, EvaluateRequest req, AuthUser teacher) {
        Submission s = requireOwnedSubmission(submissionId, teacher);
        if (s.getCurrentVersion() == 0) {
            throw ApiException.conflict("NOTHING_SUBMITTED", "Nothing has been submitted yet.");
        }
        WorkItem item = s.item();
        List<RubricCriterion> rubric = s.isAssignment()
                ? criteria.findByAssignmentIdOrderByPositionAsc(item.getId())
                : criteria.findByProjectIdOrderByPositionAsc(item.getId());
        String feedback = Texts.trimToNull(req.feedback());
        String reason = Texts.trimToNull(req.reason());
        BigDecimal total = null;
        Map<Long, BigDecimal> scoreMap = new HashMap<>();

        switch (req.decision()) {
            case EVALUATED -> {
                if (!rubric.isEmpty()) {
                    if (req.scores() != null) {
                        for (ScoreInput si : req.scores()) {
                            scoreMap.put(si.criterionId(), si.score());
                        }
                    }
                    total = BigDecimal.ZERO;
                    for (RubricCriterion rc : rubric) {
                        BigDecimal sc = scoreMap.get(rc.getId());
                        if (sc == null) {
                            throw ApiException.badRequest("SCORE_REQUIRED", "Enter a score for \"" + rc.getTitle() + "\".");
                        }
                        checkRange(sc, rc.getMaxMarks(), "\"" + rc.getTitle() + "\"");
                        total = total.add(sc);
                    }
                    if (scoreMap.size() != rubric.size()) {
                        throw ApiException.badRequest("UNKNOWN_CRITERION", "Scores must match this rubric's criteria.");
                    }
                } else {
                    if (req.marks() == null) {
                        throw ApiException.badRequest("MARKS_REQUIRED", "Enter the marks awarded.");
                    }
                    checkRange(req.marks(), item.getMaxMarks(), "Marks");
                    total = req.marks();
                }
                total = total.setScale(2, RoundingMode.UNNECESSARY);
            }
            case REVISION_REQUESTED -> {
                if (feedback == null) {
                    throw ApiException.badRequest("FEEDBACK_REQUIRED", "Explain what needs to be revised in the feedback.");
                }
            }
            case REJECTED -> {
                if (reason == null) {
                    throw ApiException.badRequest("REASON_REQUIRED", "A reason is required to reject a submission.");
                }
            }
        }

        evaluations.findFirstBySubmissionIdAndCurrentTrue(s.getId()).ifPresent(prev -> prev.setCurrent(false));
        SubmissionVersion version = versions.findBySubmissionIdAndVersionNo(s.getId(), s.getCurrentVersion()).orElse(null);
        Evaluation e = new Evaluation(s, version, users.getReferenceById(teacher.id()), req.decision(), total, feedback, reason);
        if (req.decision() == EvaluationDecision.EVALUATED) {
            for (RubricCriterion rc : rubric) {
                e.addScore(rc, scoreMap.get(rc.getId()).setScale(2, RoundingMode.UNNECESSARY));
            }
        }
        evaluations.save(e);
        if (req.publish()) {
            publishInternal(e);
        }
        return views.evaluation(e, item.getMaxMarks());
    }

    @Transactional
    public EvaluationDto publish(Long evaluationId, AuthUser teacher) {
        Evaluation e = evaluations.findById(evaluationId).orElseThrow(() -> ApiException.notFound("Evaluation"));
        requireOwnedSubmission(e.getSubmission().getId(), teacher);
        if (!e.isCurrent()) {
            throw ApiException.conflict("EVALUATION_OUTDATED", "A newer evaluation exists; publish that one instead.");
        }
        if (e.isPublished()) {
            throw ApiException.conflict("ALREADY_PUBLISHED", "This evaluation is already published.");
        }
        // A draft is tied to the version it assessed; after a resubmission it no longer describes the student's work.
        if (e.getVersion() == null || e.getVersion().getVersionNo() != e.getSubmission().getCurrentVersion()) {
            throw ApiException.conflict("EVALUATION_OUTDATED",
                    "The student submitted a newer version after this draft was saved. Evaluate the latest version.");
        }
        publishInternal(e);
        return views.evaluation(e, e.getSubmission().item().getMaxMarks());
    }

    private void publishInternal(Evaluation e) {
        e.publish();
        Submission s = e.getSubmission();
        WorkItem item = s.item();
        s.setStatus(switch (e.getDecision()) {
            case EVALUATED -> SubmissionStatus.EVALUATED;
            case REVISION_REQUESTED -> SubmissionStatus.REVISION_REQUESTED;
            case REJECTED -> SubmissionStatus.REJECTED;
        });
        List<User> recipients = s.getTeam() != null
                ? members.findByTeamIdOrderByJoinedAtAsc(s.getTeam().getId()).stream().map(TeamMember::getStudent).toList()
                : List.of(s.getStudent());
        String link = s.isAssignment() ? PageRefs.assignment(item.getId()) : PageRefs.project(item.getId());
        switch (e.getDecision()) {
            case EVALUATED -> notifications.notifyAll(recipients, NotificationType.EVALUATION_PUBLISHED,
                    "Marks published: " + item.getTitle(),
                    "You received " + e.getTotalMarks().stripTrailingZeros().toPlainString() + " / " + item.getMaxMarks()
                            + ". Open it to read the feedback.", link);
            case REVISION_REQUESTED -> notifications.notifyAll(recipients, NotificationType.REVISION_REQUESTED,
                    "Revision requested: " + item.getTitle(),
                    "Your teacher requested changes. Read the feedback and resubmit.", link);
            case REJECTED -> notifications.notifyAll(recipients, NotificationType.SUBMISSION_REJECTED,
                    "Submission rejected: " + item.getTitle(), "Reason: " + e.getReason(), link);
        }
        if (s.getTeam() != null) {
            teamService.log(s.getTeam(), e.getEvaluator(), "EVALUATION_PUBLISHED", "Evaluation published: " + e.getDecision());
        }
    }

    // ------------------------------------------------------------------ helpers

    private Submission requireOwnedSubmission(Long submissionId, AuthUser teacher) {
        Submission s = submissions.findById(submissionId).orElseThrow(() -> ApiException.notFound("Submission"));
        if (!access.isTeacherOf(s.item().getClassroom(), teacher)) {
            throw ApiException.notFound("Submission");
        }
        return s;
    }

    private Map<Long, Evaluation> currentEvaluations(List<Submission> subs) {
        if (subs.isEmpty()) {
            return Map.of();
        }
        return evaluations.findBySubmissionIdInAndCurrentTrue(subs.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(e -> e.getSubmission().getId(), Function.identity(), (a, b) -> a));
    }

    private static void checkRange(BigDecimal value, int max, String label) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw ApiException.badRequest("MARKS_OUT_OF_RANGE", label + " must be between 0 and " + max + ".");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("MARKS_PRECISION", label + " can have at most 2 decimal places.");
        }
    }

    private static boolean matchesStatus(SubmissionStatus s, String filter) {
        if ("PENDING".equalsIgnoreCase(filter)) {
            return s == SubmissionStatus.SUBMITTED || s == SubmissionStatus.LATE;
        }
        return s.name().equalsIgnoreCase(filter);
    }

    private static int priority(SubmissionStatus s) {
        return switch (s) {
            case SUBMITTED, LATE -> 0;
            case REVISION_REQUESTED -> 1;
            case EVALUATED, REJECTED -> 2;
            case NOT_SUBMITTED -> 3;
        };
    }

}
