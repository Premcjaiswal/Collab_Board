package com.collabboard.project;

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
import com.collabboard.project.ProjectDtos.MyTeamRef;
import com.collabboard.project.ProjectDtos.ProjectRequest;
import com.collabboard.project.ProjectDtos.ProjectView;
import com.collabboard.storage.FileTypes;
import com.collabboard.submission.Submission;
import com.collabboard.submission.SubmissionDtos.SubmissionSummary;
import com.collabboard.submission.SubmissionRepository;
import com.collabboard.submission.SubmissionStatus;
import com.collabboard.submission.SubmissionViews;
import com.collabboard.team.InvitationStatus;
import com.collabboard.team.Team;
import com.collabboard.team.TeamInvitationRepository;
import com.collabboard.team.TeamMember;
import com.collabboard.team.TeamMemberRepository;
import com.collabboard.team.TeamRepository;
import com.collabboard.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProjectService {

    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final ProjectRepository projects;
    private final ClassroomRepository classrooms;
    private final EnrollmentRepository enrollments;
    private final SubmissionRepository submissions;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TeamInvitationRepository invitations;
    private final UserRepository users;
    private final ClassroomAccess access;
    private final RubricService rubricService;
    private final SubmissionViews views;
    private final NotificationService notifications;

    public ProjectService(ProjectRepository projects, ClassroomRepository classrooms, EnrollmentRepository enrollments,
                          SubmissionRepository submissions, TeamRepository teams, TeamMemberRepository members,
                          TeamInvitationRepository invitations, UserRepository users, ClassroomAccess access,
                          RubricService rubricService, SubmissionViews views, NotificationService notifications) {
        this.projects = projects;
        this.classrooms = classrooms;
        this.enrollments = enrollments;
        this.submissions = submissions;
        this.teams = teams;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.access = access;
        this.rubricService = rubricService;
        this.views = views;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(AuthUser user, Long classId) {
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
        return projects.findByClassroomIdInOrderByDeadlineAsc(classIds).stream().map(p -> toView(p, user, false)).toList();
    }

    @Transactional(readOnly = true)
    public ProjectView get(Long id, AuthUser user) {
        return toView(requireVisible(id, user), user, true);
    }

    @Transactional(readOnly = true)
    public Project requireVisible(Long id, AuthUser user) {
        Project p = projects.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        if (!access.isMember(p.getClassroom(), user)) {
            throw ApiException.notFound("Project");
        }
        return p;
    }

    @Transactional
    public ProjectView create(ProjectRequest req, AuthUser teacher) {
        Classroom c = access.requireOwned(req.classId(), teacher);
        if (c.isArchived()) {
            throw ApiException.badRequest("CLASS_ARCHIVED", "Cannot add work to an archived class.");
        }
        if (!req.deadline().isAfter(Instant.now())) {
            throw ApiException.badRequest("DEADLINE_IN_PAST", "The deadline must be in the future.");
        }
        RubricService.validate(req.rubric(), req.maxMarks());
        Project p = new Project();
        p.setClassroom(c);
        p.setCreatedBy(users.getReferenceById(teacher.id()));
        p.setParticipationMode(req.participationMode());
        applyGroupSettings(p, req);
        apply(p, req);
        projects.save(p);
        rubricService.replaceForProject(p, req.rubric());
        String mode = p.isGroup() ? "Group project (teams of up to " + p.getMaxTeamSize() + ")" : "Individual project";
        notifications.notifyAll(enrollments.findStudents(c.getId()), NotificationType.NEW_PROJECT,
                "New project: " + p.getTitle(),
                mode + " in " + c.getName() + " — due " + DEADLINE_FMT.format(p.getDeadline()) + ".", PageRefs.project(p.getId()));
        return toView(p, teacher, true);
    }

    @Transactional
    public ProjectView update(Long id, ProjectRequest req, AuthUser teacher) {
        Project p = projects.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        access.requireOwned(p.getClassroom().getId(), teacher);
        if (!p.getClassroom().getId().equals(req.classId())) {
            throw ApiException.badRequest("CLASS_CHANGE_NOT_ALLOWED", "A project cannot be moved to another class.");
        }
        boolean hasActivity = teams.countByProjectId(id) > 0 || submissions.existsByProjectId(id);
        if (req.participationMode() != p.getParticipationMode()) {
            if (hasActivity) {
                throw ApiException.conflict("MODE_LOCKED", "Participation mode cannot change after teams or submissions exist.");
            }
            p.setParticipationMode(req.participationMode());
        }
        if (p.isGroup() && hasActivity && req.teamFormation() != null && req.teamFormation() != p.getTeamFormation()) {
            throw ApiException.conflict("FORMATION_LOCKED", "Team formation method cannot change after teams exist.");
        }
        Integer oldSize = p.getMaxTeamSize();
        applyGroupSettings(p, req);
        if (p.isGroup() && oldSize != null && p.getMaxTeamSize() < oldSize) {
            for (Team t : teams.findByProjectIdOrderByNameAsc(id)) {
                long used = members.countByTeamId(t.getId()) + invitations.countByTeamIdAndStatus(t.getId(), InvitationStatus.PENDING);
                if (used > p.getMaxTeamSize()) {
                    throw ApiException.conflict("TEAM_SIZE_TOO_SMALL",
                            "Team \"" + t.getName() + "\" already has " + used + " members/pending invitations.");
                }
            }
        }
        RubricService.validate(req.rubric(), req.maxMarks());
        rubricService.requireMaxMarksUnchanged(p, req.maxMarks());
        Instant oldDeadline = p.getDeadline();
        apply(p, req);
        rubricService.replaceForProject(p, req.rubric());
        if (!oldDeadline.equals(p.getDeadline())) {
            notifications.notifyAll(enrollments.findStudents(p.getClassroom().getId()), NotificationType.NEW_PROJECT,
                    "Deadline changed: " + p.getTitle(), "New deadline: " + DEADLINE_FMT.format(p.getDeadline()) + ".",
                    PageRefs.project(p.getId()));
        }
        return toView(p, teacher, true);
    }

    @Transactional
    public void delete(Long id, AuthUser teacher) {
        Project p = projects.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        access.requireOwned(p.getClassroom().getId(), teacher);
        if (submissions.existsByProjectIdAndCurrentVersionGreaterThan(id, 0)) {
            throw ApiException.conflict("HAS_SUBMISSIONS", "This project already has submissions and cannot be deleted.");
        }
        if (teams.countByProjectId(id) > 0) {
            throw ApiException.conflict("HAS_TEAMS", "Delete the project's teams before deleting the project.");
        }
        submissions.deleteAll(submissions.findByProjectId(id));
        projects.delete(p);
    }

    private void applyGroupSettings(Project p, ProjectRequest req) {
        if (p.isGroup()) {
            // The teacher must choose how teams are formed and the maximum team size; there are no defaults.
            // On edit, an omitted value keeps the project's current choice.
            if (req.teamFormation() != null) {
                p.setTeamFormation(req.teamFormation());
            } else if (p.getTeamFormation() == null) {
                throw ApiException.badRequest("TEAM_FORMATION_REQUIRED",
                        "Choose how teams are formed: student-created teams or teacher-assigned teams.");
            }
            if (req.maxTeamSize() != null) {
                p.setMaxTeamSize(req.maxTeamSize());
            } else if (p.getMaxTeamSize() == null) {
                throw ApiException.badRequest("TEAM_SIZE_REQUIRED", "Choose the maximum team size (2 to 20).");
            }
        } else {
            p.setTeamFormation(null);
            p.setMaxTeamSize(null);
        }
    }

    private void apply(Project p, ProjectRequest req) {
        p.setTitle(req.title().trim());
        p.setDescription(req.description().trim());
        p.setDeadline(req.deadline());
        p.setMaxMarks(req.maxMarks());
        p.setAllowedExtensions(FileTypes.normalizeAllowed(req.allowedExtensions()));
        p.setMaxFileSizeMb(req.maxFileSizeMb());
        p.setAllowResubmission(req.allowResubmission());
        p.setAllowLate(req.allowLate());
    }

    ProjectView toView(Project p, AuthUser user, boolean withRubric) {
        Classroom c = p.getClassroom();
        boolean teacher = access.isTeacherOf(c, user);
        SubmissionSummary mine = null;
        MyTeamRef myTeam = null;
        int pending = 0;
        if (user.isStudent()) {
            if (p.isGroup()) {
                TeamMember m = members.findByProjectIdAndStudentId(p.getId(), user.id()).orElse(null);
                if (m != null) {
                    Team t = m.getTeam();
                    myTeam = new MyTeamRef(t.getId(), t.getName(), (int) members.countByTeamId(t.getId()));
                    mine = views.summary(submissions.findByProjectIdAndTeamId(p.getId(), t.getId()).orElse(null), p);
                } else {
                    mine = views.summary(null, p);
                    pending = invitations.findByProjectIdAndInviteeIdAndStatus(p.getId(), user.id(), InvitationStatus.PENDING).size();
                }
            } else {
                mine = views.summary(submissions.findByProjectIdAndStudentId(p.getId(), user.id()).orElse(null), p);
            }
        }
        return new ProjectView(p.getId(), new ClassRef(c.getId(), c.getName(), c.displayName(), c.getSubjectName()),
                p.getTitle(), p.getDescription(), p.getDeadline(), Instant.now().isAfter(p.getDeadline()), p.getMaxMarks(),
                p.getParticipationMode(), p.getTeamFormation(), p.getMaxTeamSize(),
                List.copyOf(Texts.parseExtensions(p.getAllowedExtensions())), p.getMaxFileSizeMb(),
                p.isAllowResubmission(), p.isAllowLate(), withRubric ? rubricService.forProject(p.getId()) : List.of(),
                p.getCreatedAt(), mine, myTeam, pending, teacher ? stats(p) : null,
                p.isGroup() ? teams.countByProjectId(p.getId()) : 0);
    }

    private ItemStats stats(Project p) {
        List<Submission> subs = submissions.findByProjectId(p.getId());
        long expected = p.isGroup() ? teams.countByProjectId(p.getId()) : enrollments.countByClassroomId(p.getClassroom().getId());
        long submitted = subs.stream().filter(s -> s.getCurrentVersion() > 0).count();
        long evaluated = subs.stream().filter(s -> s.getStatus() == SubmissionStatus.EVALUATED
                || s.getStatus() == SubmissionStatus.REJECTED).count();
        long pending = subs.stream().filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED
                || s.getStatus() == SubmissionStatus.LATE).count();
        return new ItemStats(expected, submitted, evaluated, pending);
    }
}
