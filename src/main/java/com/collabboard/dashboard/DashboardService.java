package com.collabboard.dashboard;

import com.collabboard.assignment.Assignment;
import com.collabboard.assignment.AssignmentRepository;
import com.collabboard.auth.AuthUser;
import com.collabboard.classroom.Classroom;
import com.collabboard.classroom.ClassroomRepository;
import com.collabboard.enrollment.EnrollmentRepository;
import com.collabboard.evaluation.Evaluation;
import com.collabboard.evaluation.EvaluationRepository;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationService.NotificationDto;
import com.collabboard.project.Project;
import com.collabboard.project.ProjectRepository;
import com.collabboard.submission.Submission;
import com.collabboard.submission.SubmissionRepository;
import com.collabboard.submission.SubmissionStatus;
import com.collabboard.submission.WorkItem;
import com.collabboard.team.InvitationStatus;
import com.collabboard.team.TeamInvitationRepository;
import com.collabboard.team.TeamMember;
import com.collabboard.team.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Aggregated, role-specific dashboard data. Everything is scoped to the authenticated user. */
@Service
public class DashboardService {

    public record ClassCard(Long id, String displayName, String subjectName, long students, String teacherName, boolean archived) {
    }

    public record DeadlineItem(String kind, Long id, String title, Long classId, String className, Instant deadline,
                               String status, boolean group) {
    }

    public record ResultItem(String kind, Long itemId, String title, String className, String decision,
                             BigDecimal marks, int maxMarks, String feedback, Instant publishedAt) {
    }

    public record RecentSubmission(Long submissionId, String kind, Long itemId, String title, String className,
                                   String owner, String status, Instant submittedAt) {
    }

    public record StudentDashboard(List<ClassCard> classes, List<DeadlineItem> upcomingAssignments,
                                   List<DeadlineItem> upcomingProjects, List<DeadlineItem> pending,
                                   List<RecentSubmission> recentSubmissions, List<ResultItem> results,
                                   int pendingInvitations, List<NotificationDto> notifications, long unreadNotifications) {
    }

    public record TeacherDashboard(List<ClassCard> classes, long totalStudents, long assignmentsCreated,
                                   long projectsCreated, long pendingSubmissions, long awaitingPublication,
                                   List<RecentSubmission> pendingQueue, List<RecentSubmission> recentlyEvaluated,
                                   List<DeadlineItem> upcomingDeadlines, List<NotificationDto> notifications,
                                   long unreadNotifications) {
    }

    private final ClassroomRepository classrooms;
    private final EnrollmentRepository enrollments;
    private final AssignmentRepository assignments;
    private final ProjectRepository projects;
    private final SubmissionRepository submissions;
    private final EvaluationRepository evaluations;
    private final TeamMemberRepository members;
    private final TeamInvitationRepository invitations;
    private final NotificationService notifications;

    public DashboardService(ClassroomRepository classrooms, EnrollmentRepository enrollments,
                            AssignmentRepository assignments, ProjectRepository projects,
                            SubmissionRepository submissions, EvaluationRepository evaluations,
                            TeamMemberRepository members, TeamInvitationRepository invitations,
                            NotificationService notifications) {
        this.classrooms = classrooms;
        this.enrollments = enrollments;
        this.assignments = assignments;
        this.projects = projects;
        this.submissions = submissions;
        this.evaluations = evaluations;
        this.members = members;
        this.invitations = invitations;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public StudentDashboard student(AuthUser user) {
        Instant now = Instant.now();
        List<Classroom> classes = classrooms.findEnrolledClasses(user.id());
        List<Long> classIds = classes.stream().map(Classroom::getId).toList();
        List<Assignment> as = classIds.isEmpty() ? List.of() : assignments.findByClassroomIdInOrderByDeadlineAsc(classIds);
        List<Project> ps = classIds.isEmpty() ? List.of() : projects.findByClassroomIdInOrderByDeadlineAsc(classIds);

        Map<Long, TeamMember> myTeams = members.findByStudentId(user.id()).stream()
                .collect(Collectors.toMap(m -> m.getProject().getId(), Function.identity(), (a, b) -> a));
        List<Submission> mySubs = new ArrayList<>(submissions.findByStudentId(user.id()));
        if (!myTeams.isEmpty()) {
            mySubs.addAll(submissions.findByTeamIdIn(myTeams.values().stream().map(m -> m.getTeam().getId()).toList()));
        }
        // Work from classes the student was removed from stays with the teacher, not on the student's dashboard.
        mySubs.removeIf(s -> !classIds.contains(s.item().getClassroom().getId()));
        Map<Long, Submission> byAssignment = mySubs.stream().filter(Submission::isAssignment)
                .collect(Collectors.toMap(s -> s.getAssignment().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Submission> byProject = mySubs.stream().filter(s -> !s.isAssignment())
                .collect(Collectors.toMap(s -> s.getProject().getId(), Function.identity(), (a, b) -> a));

        List<DeadlineItem> upcomingA = as.stream().filter(a -> a.getDeadline().isAfter(now)).limit(8)
                .map(a -> deadline("ASSIGNMENT", a, statusOf(byAssignment.get(a.getId())), false)).toList();
        List<DeadlineItem> upcomingP = ps.stream().filter(p -> p.getDeadline().isAfter(now)).limit(8)
                .map(p -> deadline("PROJECT", p, statusOf(byProject.get(p.getId())), p.isGroup())).toList();

        List<DeadlineItem> pending = new ArrayList<>();
        as.stream().filter(a -> needsAction(a, byAssignment.get(a.getId()), now))
                .forEach(a -> pending.add(deadline("ASSIGNMENT", a, statusOf(byAssignment.get(a.getId())), false)));
        ps.stream().filter(p -> needsAction(p, byProject.get(p.getId()), now))
                .forEach(p -> pending.add(deadline("PROJECT", p, statusOf(byProject.get(p.getId())), p.isGroup())));
        pending.sort(Comparator.comparing(DeadlineItem::deadline));

        List<RecentSubmission> recent = mySubs.stream().filter(s -> s.getCurrentVersion() > 0)
                .sorted(Comparator.comparing(Submission::getLastSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6).map(this::recent).toList();

        List<ResultItem> results = mySubs.stream()
                .map(s -> evaluations.findFirstBySubmissionIdAndPublishedTrueOrderByPublishedAtDescIdDesc(s.getId())
                        .map(e -> result(s, e)).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ResultItem::publishedAt).reversed())
                .limit(6).toList();

        List<ClassCard> cards = classes.stream().map(this::card).toList();
        int invites = (int) invitations.findByInviteeIdAndStatusOrderByCreatedAtDesc(user.id(), InvitationStatus.PENDING).stream()
                .filter(i -> classIds.contains(i.getProject().getClassroom().getId())).count();
        return new StudentDashboard(cards, upcomingA, upcomingP, pending, recent, results, invites,
                notifications.list(user.id(), false, 6), notifications.unreadCount(user.id()));
    }

    @Transactional(readOnly = true)
    public TeacherDashboard teacher(AuthUser user) {
        Instant now = Instant.now();
        List<Classroom> classes = classrooms.findByTeacherIdOrderByCreatedAtDesc(user.id());
        List<Long> classIds = classes.stream().map(Classroom::getId).toList();
        List<Assignment> as = classIds.isEmpty() ? List.of() : assignments.findByClassroomIdInOrderByDeadlineAsc(classIds);
        List<Project> ps = classIds.isEmpty() ? List.of() : projects.findByClassroomIdInOrderByDeadlineAsc(classIds);
        List<Submission> subs = submissions.findSubmittedForTeacher(user.id());

        List<Submission> pendingSubs = subs.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED || s.getStatus() == SubmissionStatus.LATE).toList();
        Map<Long, Evaluation> current = subs.isEmpty() ? Map.of()
                : evaluations.findBySubmissionIdInAndCurrentTrue(subs.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(e -> e.getSubmission().getId(), Function.identity(), (a, b) -> a));
        long awaitingPublication = current.values().stream().filter(e -> !e.isPublished()).count();

        List<RecentSubmission> queue = pendingSubs.stream()
                .sorted(Comparator.comparing(Submission::getLastSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(8).map(this::recent).toList();
        List<RecentSubmission> evaluated = subs.stream()
                .filter(s -> current.containsKey(s.getId()) && current.get(s.getId()).isPublished())
                .sorted(Comparator.comparing((Submission s) -> current.get(s.getId()).getPublishedAt()).reversed())
                .limit(6).map(this::recent).toList();

        List<DeadlineItem> upcoming = new ArrayList<>();
        as.stream().filter(a -> a.getDeadline().isAfter(now)).forEach(a -> upcoming.add(deadline("ASSIGNMENT", a, null, false)));
        ps.stream().filter(p -> p.getDeadline().isAfter(now)).forEach(p -> upcoming.add(deadline("PROJECT", p, null, p.isGroup())));
        upcoming.sort(Comparator.comparing(DeadlineItem::deadline));

        List<ClassCard> cards = classes.stream().map(this::card).toList();
        long totalStudents = cards.stream().mapToLong(ClassCard::students).sum();
        return new TeacherDashboard(cards, totalStudents, as.size(), ps.size(), pendingSubs.size(), awaitingPublication,
                queue, evaluated, upcoming.stream().limit(8).toList(),
                notifications.list(user.id(), false, 6), notifications.unreadCount(user.id()));
    }

    private boolean needsAction(WorkItem item, Submission s, Instant now) {
        SubmissionStatus st = s == null ? SubmissionStatus.NOT_SUBMITTED : s.getStatus();
        if (st == SubmissionStatus.REVISION_REQUESTED) {
            return true;
        }
        if (st != SubmissionStatus.NOT_SUBMITTED || (s != null && s.getCurrentVersion() > 0)) {
            return false;
        }
        return item.getDeadline().isAfter(now) || item.isAllowLate();
    }

    private static String statusOf(Submission s) {
        return s == null ? SubmissionStatus.NOT_SUBMITTED.name() : s.getStatus().name();
    }

    private DeadlineItem deadline(String kind, WorkItem item, String status, boolean group) {
        Classroom c = item.getClassroom();
        return new DeadlineItem(kind, item.getId(), item.getTitle(), c.getId(), c.displayName(), item.getDeadline(), status, group);
    }

    private RecentSubmission recent(Submission s) {
        WorkItem item = s.item();
        String owner = s.getTeam() != null ? s.getTeam().getName() : s.getStudent().getFullName();
        return new RecentSubmission(s.getId(), s.isAssignment() ? "ASSIGNMENT" : "PROJECT", item.getId(), item.getTitle(),
                item.getClassroom().displayName(), owner, s.getStatus().name(), s.getLastSubmittedAt());
    }

    private ResultItem result(Submission s, Evaluation e) {
        WorkItem item = s.item();
        return new ResultItem(s.isAssignment() ? "ASSIGNMENT" : "PROJECT", item.getId(), item.getTitle(),
                item.getClassroom().displayName(), e.getDecision().name(), e.getTotalMarks(), item.getMaxMarks(),
                e.getFeedback(), e.getPublishedAt());
    }

    private ClassCard card(Classroom c) {
        return new ClassCard(c.getId(), c.displayName(), c.getSubjectName(), enrollments.countByClassroomId(c.getId()),
                c.getTeacher().getFullName(), c.isArchived());
    }
}
