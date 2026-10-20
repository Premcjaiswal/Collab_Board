package com.collabboard.assignment;

import com.collabboard.assignment.AssignmentDtos.AssignmentRequest;
import com.collabboard.assignment.AssignmentDtos.AssignmentView;
import com.collabboard.assignment.AssignmentDtos.ClassRef;
import com.collabboard.assignment.AssignmentDtos.ItemStats;
import com.collabboard.auth.AuthUser;
import com.collabboard.classroom.Classroom;
import com.collabboard.classroom.ClassroomAccess;
import com.collabboard.classroom.ClassroomRepository;
import com.collabboard.common.ApiException;
import com.collabboard.common.PageRefs;
import com.collabboard.common.Texts;
import com.collabboard.enrollment.EnrollmentRepository;
import com.collabboard.evaluation.RubricService;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.storage.FileTypes;
import com.collabboard.submission.Submission;
import com.collabboard.submission.SubmissionRepository;
import com.collabboard.submission.SubmissionStatus;
import com.collabboard.submission.SubmissionViews;
import com.collabboard.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AssignmentService {

    static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final AssignmentRepository assignments;
    private final ClassroomRepository classrooms;
    private final EnrollmentRepository enrollments;
    private final SubmissionRepository submissions;
    private final UserRepository users;
    private final ClassroomAccess access;
    private final RubricService rubricService;
    private final SubmissionViews views;
    private final NotificationService notifications;

    public AssignmentService(AssignmentRepository assignments, ClassroomRepository classrooms,
                             EnrollmentRepository enrollments, SubmissionRepository submissions, UserRepository users,
                             ClassroomAccess access, RubricService rubricService, SubmissionViews views,
                             NotificationService notifications) {
        this.assignments = assignments;
        this.classrooms = classrooms;
        this.enrollments = enrollments;
        this.submissions = submissions;
        this.users = users;
        this.access = access;
        this.rubricService = rubricService;
        this.views = views;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> list(AuthUser user, Long classId) {
        List<Long> classIds;
        if (classId != null) {
            classIds = List.of(access.requireMember(classId, user).getId());
        } else if (user.isTeacher()) {
            classIds = classrooms.findByTeacherIdOrderByCreatedAtDesc(user.id()).stream().map(Classroom::getId).toList();
        } else {
            classIds = classrooms.findEnrolledClasses(user.id()).stream().map(Classroom::getId).toList();
        }
        if (classIds.isEmpty()) {
            return List.of();
        }
        return assignments.findByClassroomIdInOrderByDeadlineAsc(classIds).stream()
                .map(a -> toView(a, user, false)).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentView get(Long id, AuthUser user) {
        return toView(requireVisible(id, user), user, true);
    }

    /** Loads an assignment if the user teaches or is enrolled in its class. */
    @Transactional(readOnly = true)
    public Assignment requireVisible(Long id, AuthUser user) {
        Assignment a = assignments.findById(id).orElseThrow(() -> ApiException.notFound("Assignment"));
        if (!access.isMember(a.getClassroom(), user)) {
            throw ApiException.notFound("Assignment");
        }
        return a;
    }

    @Transactional
    public AssignmentView create(AssignmentRequest req, AuthUser teacher) {
        Classroom c = access.requireOwned(req.classId(), teacher);
        if (c.isArchived()) {
            throw ApiException.badRequest("CLASS_ARCHIVED", "Cannot add work to an archived class.");
        }
        if (!req.deadline().isAfter(Instant.now())) {
            throw ApiException.badRequest("DEADLINE_IN_PAST", "The deadline must be in the future.");
        }
        RubricService.validate(req.rubric(), req.maxMarks());
        Assignment a = new Assignment();
        a.setClassroom(c);
        a.setCreatedBy(users.getReferenceById(teacher.id()));
        apply(a, req);
        assignments.save(a);
        rubricService.replaceForAssignment(a, req.rubric());
        notifications.notifyAll(enrollments.findStudents(c.getId()), NotificationType.NEW_ASSIGNMENT,
                "New assignment: " + a.getTitle(),
                c.getName() + " — due " + DEADLINE_FMT.format(a.getDeadline()) + ".", PageRefs.assignment(a.getId()));
        return toView(a, teacher, true);
    }

    @Transactional
    public AssignmentView update(Long id, AssignmentRequest req, AuthUser teacher) {
        Assignment a = assignments.findById(id).orElseThrow(() -> ApiException.notFound("Assignment"));
        access.requireOwned(a.getClassroom().getId(), teacher);
        if (!a.getClassroom().getId().equals(req.classId())) {
            throw ApiException.badRequest("CLASS_CHANGE_NOT_ALLOWED", "An assignment cannot be moved to another class.");
        }
        RubricService.validate(req.rubric(), req.maxMarks());
        rubricService.requireMaxMarksUnchanged(a, req.maxMarks());
        Instant oldDeadline = a.getDeadline();
        apply(a, req);
        rubricService.replaceForAssignment(a, req.rubric());
        if (!oldDeadline.equals(a.getDeadline())) {
            notifications.notifyAll(enrollments.findStudents(a.getClassroom().getId()), NotificationType.NEW_ASSIGNMENT,
                    "Deadline changed: " + a.getTitle(), "New deadline: " + DEADLINE_FMT.format(a.getDeadline()) + ".",
                    PageRefs.assignment(a.getId()));
        }
        return toView(a, teacher, true);
    }

    @Transactional
    public void delete(Long id, AuthUser teacher) {
        Assignment a = assignments.findById(id).orElseThrow(() -> ApiException.notFound("Assignment"));
        access.requireOwned(a.getClassroom().getId(), teacher);
        if (submissions.existsByAssignmentIdAndCurrentVersionGreaterThan(id, 0)) {
            throw ApiException.conflict("HAS_SUBMISSIONS",
                    "This assignment already has submissions and cannot be deleted. Edit it instead.");
        }
        submissions.deleteAll(submissions.findByAssignmentId(id));
        assignments.delete(a);
    }

    private void apply(Assignment a, AssignmentRequest req) {
        a.setTitle(req.title().trim());
        a.setDescription(req.description().trim());
        a.setCategory(req.category());
        a.setDeadline(req.deadline());
        a.setMaxMarks(req.maxMarks());
        a.setAllowedExtensions(FileTypes.normalizeAllowed(req.allowedExtensions()));
        a.setMaxFileSizeMb(req.maxFileSizeMb());
        a.setAllowResubmission(req.allowResubmission());
        a.setAllowLate(req.allowLate());
    }

    AssignmentView toView(Assignment a, AuthUser user, boolean withRubric) {
        Classroom c = a.getClassroom();
        boolean teacher = access.isTeacherOf(c, user);
        var mine = user.isStudent()
                ? views.summary(submissions.findByAssignmentIdAndStudentId(a.getId(), user.id()).orElse(null), a)
                : null;
        ItemStats stats = teacher ? stats(a) : null;
        return new AssignmentView(a.getId(), new ClassRef(c.getId(), c.getName(), c.displayName(), c.getSubjectName()),
                a.getTitle(), a.getDescription(), a.getCategory(), a.getDeadline(), Instant.now().isAfter(a.getDeadline()),
                a.getMaxMarks(), List.copyOf(Texts.parseExtensions(a.getAllowedExtensions())), a.getMaxFileSizeMb(),
                a.isAllowResubmission(), a.isAllowLate(),
                withRubric ? rubricService.forAssignment(a.getId()) : List.of(), a.getCreatedAt(), mine, stats);
    }

    private ItemStats stats(Assignment a) {
        List<Submission> subs = submissions.findByAssignmentId(a.getId());
        long submitted = subs.stream().filter(s -> s.getCurrentVersion() > 0).count();
        long evaluated = subs.stream().filter(s -> s.getStatus() == SubmissionStatus.EVALUATED
                || s.getStatus() == SubmissionStatus.REJECTED).count();
        long pending = subs.stream().filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED
                || s.getStatus() == SubmissionStatus.LATE).count();
        return new ItemStats(enrollments.countByClassroomId(a.getClassroom().getId()), submitted, evaluated, pending);
    }
}
