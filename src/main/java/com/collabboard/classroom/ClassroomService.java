package com.collabboard.classroom;

import com.collabboard.assignment.AssignmentRepository;
import com.collabboard.auth.AuthUser;
import com.collabboard.classroom.ClassroomDtos.ClassRequest;
import com.collabboard.classroom.ClassroomDtos.ClassSummary;
import com.collabboard.classroom.ClassroomDtos.RosterEntry;
import com.collabboard.common.ApiException;
import com.collabboard.common.JoinCodes;
import com.collabboard.common.PageRefs;
import com.collabboard.common.Texts;
import com.collabboard.enrollment.Enrollment;
import com.collabboard.enrollment.EnrollmentRepository;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.project.ProjectRepository;
import com.collabboard.team.TeamService;
import com.collabboard.user.StudentProfile;
import com.collabboard.user.StudentProfileRepository;
import com.collabboard.user.User;
import com.collabboard.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClassroomService {

    /** Unambiguous characters (no 0/O, 1/I/L). */

    private final ClassroomRepository classrooms;
    private final EnrollmentRepository enrollments;
    private final AssignmentRepository assignments;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final ClassroomAccess access;
    private final NotificationService notifications;
    private final TeamService teamService;

    public ClassroomService(ClassroomRepository classrooms, EnrollmentRepository enrollments,
                            AssignmentRepository assignments, ProjectRepository projects, UserRepository users,
                            StudentProfileRepository studentProfiles, ClassroomAccess access,
                            NotificationService notifications, TeamService teamService) {
        this.classrooms = classrooms;
        this.enrollments = enrollments;
        this.assignments = assignments;
        this.projects = projects;
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.access = access;
        this.notifications = notifications;
        this.teamService = teamService;
    }

    @Transactional(readOnly = true)
    public List<ClassSummary> list(AuthUser user) {
        if (user.isTeacher()) {
            return classrooms.findByTeacherIdOrderByCreatedAtDesc(user.id()).stream()
                    .map(c -> toSummary(c, true, null)).toList();
        }
        return enrollments.findByStudentId(user.id()).stream()
                .sorted((a, b) -> b.getJoinedAt().compareTo(a.getJoinedAt()))
                .map(e -> toSummary(e.getClassroom(), false, e.getJoinedAt())).toList();
    }

    @Transactional(readOnly = true)
    public ClassSummary get(Long id, AuthUser user) {
        Classroom c = access.requireMember(id, user);
        Enrollment e = user.isStudent() ? enrollments.findByClassroomIdAndStudentId(id, user.id()).orElse(null) : null;
        return toSummary(c, access.isTeacherOf(c, user), e == null ? null : e.getJoinedAt());
    }

    @Transactional
    public ClassSummary create(ClassRequest req, AuthUser teacher) {
        Classroom c = new Classroom();
        c.setTeacher(users.getReferenceById(teacher.id()));
        apply(c, req);
        c.setJoinCode(newJoinCode());
        classrooms.save(c);
        return toSummary(c, true, null);
    }

    @Transactional
    public ClassSummary update(Long id, ClassRequest req, AuthUser teacher) {
        Classroom c = access.requireOwned(id, teacher);
        apply(c, req);
        return toSummary(c, true, null);
    }

    @Transactional
    public ClassSummary setArchived(Long id, boolean archived, AuthUser teacher) {
        Classroom c = access.requireOwned(id, teacher);
        c.setArchived(archived);
        return toSummary(c, true, null);
    }

    @Transactional
    public ClassSummary regenerateCode(Long id, AuthUser teacher) {
        Classroom c = access.requireOwned(id, teacher);
        c.setJoinCode(newJoinCode());
        return toSummary(c, true, null);
    }

    /** Valid join codes enroll the student immediately; no teacher approval is required. */
    @Transactional
    public ClassSummary join(String rawCode, AuthUser student) {
        String code = JoinCodes.normalize(rawCode);
        Classroom c = classrooms.findByJoinCode(code)
                .orElseThrow(() -> ApiException.badRequest("INVALID_CLASS_CODE", "No class matches that code. Check it and try again."));
        if (c.isArchived()) {
            throw ApiException.badRequest("CLASS_ARCHIVED", "This class is archived and no longer accepts new students.");
        }
        if (enrollments.existsByClassroomIdAndStudentId(c.getId(), student.id())) {
            throw ApiException.conflict("ALREADY_ENROLLED", "You are already enrolled in " + c.displayName() + ".");
        }
        User studentUser = users.getReferenceById(student.id());
        Enrollment e = enrollments.save(new Enrollment(c, studentUser));
        notifications.notify(studentUser, NotificationType.CLASS_ENROLLMENT, "Joined " + c.displayName(),
                "You are now enrolled in " + c.displayName() + " (" + c.getSubjectName() + ").", PageRefs.classroom(c.getId()));
        User s = users.findById(student.id()).orElseThrow();
        notifications.notify(c.getTeacher(), NotificationType.CLASS_ENROLLMENT, "New student in " + c.getName(),
                s.getFullName() + " joined " + c.displayName() + ".", PageRefs.classroom(c.getId()));
        return toSummary(c, false, e.getJoinedAt());
    }

    @Transactional(readOnly = true)
    public List<RosterEntry> roster(Long id, AuthUser teacher) {
        access.requireOwned(id, teacher);
        List<Enrollment> list = enrollments.findByClassroomIdOrderByJoinedAtAsc(id);
        Map<Long, StudentProfile> profiles = studentProfiles
                .findByUserIdIn(list.stream().map(e -> e.getStudent().getId()).toList()).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, Function.identity()));
        return list.stream().map(e -> {
            User s = e.getStudent();
            StudentProfile p = profiles.get(s.getId());
            return new RosterEntry(s.getId(), s.getFullName(), s.getEmail(), p == null ? null : p.getRollNumber(),
                    p == null ? null : p.getBranch(), e.getJoinedAt());
        }).toList();
    }

    /**
     * Teacher removes a student from the class. The student also leaves any project teams in this class
     * and pending invitations are cancelled. Submissions and evaluations are preserved.
     */
    @Transactional
    public void removeStudent(Long classId, Long studentId, AuthUser teacher) {
        Classroom c = access.requireOwned(classId, teacher);
        Enrollment e = enrollments.findByClassroomIdAndStudentId(classId, studentId)
                .orElseThrow(() -> ApiException.notFound("Enrollment"));
        teamService.detachStudentFromClass(c, e.getStudent(), users.getReferenceById(teacher.id()));
        enrollments.delete(e);
        notifications.notify(e.getStudent(), NotificationType.CLASS_ENROLLMENT, "Removed from " + c.getName(),
                "Your teacher removed you from " + c.displayName() + ".", "#/classes");
    }

    private void apply(Classroom c, ClassRequest req) {
        c.setName(req.name().trim());
        c.setSubjectName(req.subjectName().trim());
        c.setSemester(req.semester().trim());
        c.setDivision(Texts.trimToNull(req.division()));
        c.setDescription(Texts.trimToNull(req.description()));
    }

    private String newJoinCode() {
        return JoinCodes.generate(classrooms::existsByJoinCode);
    }

    ClassSummary toSummary(Classroom c, boolean isTeacher, java.time.Instant joinedAt) {
        return new ClassSummary(c.getId(), c.getName(), c.getSubjectName(), c.getSemester(), c.getDivision(),
                c.getDescription(), c.displayName(), isTeacher ? c.getJoinCode() : null, c.getTeacher().getFullName(),
                enrollments.countByClassroomId(c.getId()), assignments.countByClassroomId(c.getId()),
                projects.countByClassroomId(c.getId()), c.isArchived(), c.getCreatedAt(), joinedAt);
    }
}
