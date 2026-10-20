package com.collabboard.team;

import com.collabboard.auth.AuthUser;
import com.collabboard.classroom.Classroom;
import com.collabboard.classroom.ClassroomAccess;
import com.collabboard.common.ApiException;
import com.collabboard.common.JoinCodes;
import com.collabboard.common.PageRefs;
import com.collabboard.common.Texts;
import com.collabboard.enrollment.EnrollmentRepository;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.project.Project;
import com.collabboard.project.ProjectRepository;
import com.collabboard.project.TeamFormation;
import com.collabboard.submission.Submission;
import com.collabboard.submission.SubmissionRepository;
import com.collabboard.submission.SubmissionStatus;
import com.collabboard.submission.SubmissionViews;
import com.collabboard.team.TeamDtos.CreateTeamRequest;
import com.collabboard.team.TeamDtos.InvitationDto;
import com.collabboard.team.TeamDtos.MemberDto;
import com.collabboard.team.TeamDtos.MyTeamResponse;
import com.collabboard.team.TeamDtos.TeamDto;
import com.collabboard.user.ProfileDtos.UserSummary;
import com.collabboard.user.ProfileService;
import com.collabboard.user.Role;
import com.collabboard.user.User;
import com.collabboard.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Team formation for group projects. All current members have equal rights; there is no leader.
 * Invariants enforced here (and by database keys):
 * <ul>
 *   <li>a student belongs to at most one team per project;</li>
 *   <li>members + pending invitations never exceed the project's maximum team size;</li>
 *   <li>only enrolled students of the project's class can be members or invitees;</li>
 *   <li>a pending invitation grants no workspace access.</li>
 * </ul>
 */
@Service
public class TeamService {

    private final ProjectRepository projects;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TeamInvitationRepository invitations;
    private final WorkspaceFileRepository workspaceFiles;
    private final ActivityLogRepository activities;
    private final SubmissionRepository submissions;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final ClassroomAccess access;
    private final ProfileService profiles;
    private final NotificationService notifications;
    private final SubmissionViews views;

    public TeamService(ProjectRepository projects, TeamRepository teams, TeamMemberRepository members,
                       TeamInvitationRepository invitations, WorkspaceFileRepository workspaceFiles,
                       ActivityLogRepository activities, SubmissionRepository submissions, UserRepository users,
                       EnrollmentRepository enrollments, ClassroomAccess access, ProfileService profiles,
                       NotificationService notifications, SubmissionViews views) {
        this.projects = projects;
        this.teams = teams;
        this.members = members;
        this.invitations = invitations;
        this.workspaceFiles = workspaceFiles;
        this.activities = activities;
        this.submissions = submissions;
        this.users = users;
        this.enrollments = enrollments;
        this.access = access;
        this.profiles = profiles;
        this.notifications = notifications;
        this.views = views;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<TeamDto> listTeams(Long projectId, AuthUser teacher) {
        requireGroupProject(projectId, teacher);
        return teams.findByProjectIdOrderByNameAsc(projectId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MyTeamResponse myTeam(Long projectId, AuthUser student) {
        requireGroupProject(projectId, student);
        TeamDto team = members.findByProjectIdAndStudentId(projectId, student.id())
                .map(m -> toDto(m.getTeam())).orElse(null);
        List<InvitationDto> invs = invitations
                .findByProjectIdAndInviteeIdAndStatus(projectId, student.id(), InvitationStatus.PENDING)
                .stream().map(this::toDto).toList();
        return new MyTeamResponse(team, invs);
    }

    @Transactional(readOnly = true)
    public List<InvitationDto> myInvitations(AuthUser student) {
        return invitations.findByInviteeIdAndStatusOrderByCreatedAtDesc(student.id(), InvitationStatus.PENDING).stream()
                .filter(i -> access.isEnrolled(i.getProject().getClassroom(), student.id()))
                .map(this::toDto).toList();
    }

    /** Enrolled classmates who are not yet in a team for the project (and not already invited by this team). */
    @Transactional(readOnly = true)
    public List<UserSummary> eligibleStudents(Long projectId, AuthUser user) {
        Project p = requireGroupProject(projectId, user);
        Set<Long> excluded = members.findByProjectId(projectId).stream()
                .map(m -> m.getStudent().getId()).collect(Collectors.toSet());
        if (!user.isTeacher()) {
            TeamMember mine = members.findByProjectIdAndStudentId(projectId, user.id())
                    .orElseThrow(() -> ApiException.forbidden("Join or create a team first."));
            invitations.findByTeamIdAndStatus(mine.getTeam().getId(), InvitationStatus.PENDING)
                    .forEach(i -> excluded.add(i.getInvitee().getId()));
        }
        List<User> candidates = enrollments.findStudents(p.getClassroom().getId()).stream()
                .filter(u -> !excluded.contains(u.getId()))
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return profiles.summaries(candidates);
    }

    // ------------------------------------------------------------------ team creation

    @Transactional
    public TeamDto createTeam(Long projectId, CreateTeamRequest req, AuthUser user) {
        Project p = requireGroupProject(projectId, user);
        ClassroomAccess.requireNotArchived(p.getClassroom());
        String name = req.name().trim();
        if (teams.existsByProjectIdAndNameIgnoreCase(projectId, name)) {
            throw ApiException.conflict("TEAM_NAME_TAKEN", "A team with this name already exists for the project.");
        }
        User actor = users.getReferenceById(user.id());
        if (user.isTeacher()) {
            Set<Long> ids = req.studentIds() == null ? Set.of() : new LinkedHashSet<>(req.studentIds());
            if (ids.size() > p.getMaxTeamSize()) {
                throw ApiException.badRequest("TEAM_TOO_LARGE", "A team can have at most " + p.getMaxTeamSize() + " members.");
            }
            Team team = teams.save(newTeam(p, name, actor));
            log(team, actor, "TEAM_CREATED", "Team created by teacher");
            for (Long sid : ids) {
                addMemberAsTeacher(team, sid, actor);
            }
            return toDto(team);
        }
        if (p.getTeamFormation() != TeamFormation.STUDENT_INVITE) {
            throw ApiException.forbidden("Your teacher assigns teams for this project.");
        }
        if (members.findByProjectIdAndStudentId(projectId, user.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_IN_TEAM", "You are already in a team for this project.");
        }
        Team team = teams.save(newTeam(p, name, actor));
        members.save(new TeamMember(team, actor));
        cancelPendingInvitationsFor(p, user.id());
        log(team, actor, "TEAM_CREATED", "Team \"" + name + "\" created");
        return toDto(team);
    }

    // ------------------------------------------------------------------ joining with a team code

    /**
     * Joins a student-created team with the code its members shared. The same eligibility rules as accepting an
     * invitation apply (enrolled in the class, not in another team, team not locked or full); the team row lock
     * serialises this with invitations, accepts and other joins.
     */
    @Transactional
    public TeamDto joinByCode(Long projectId, String rawCode, AuthUser student) {
        Project p = requireGroupProject(projectId, student);
        if (p.getTeamFormation() != TeamFormation.STUDENT_INVITE) {
            throw ApiException.forbidden("Your teacher assigns teams for this project.");
        }
        ClassroomAccess.requireNotArchived(p.getClassroom());
        Team found = teams.findByProjectIdAndJoinCode(projectId, JoinCodes.normalize(rawCode))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TEAM_CODE", "That team code is not valid for this project."));
        Team team = lockTeam(found.getId());
        if (members.findByProjectIdAndStudentId(projectId, student.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_IN_TEAM", "You are already in a team for this project. Leave it first.");
        }
        ensureMembershipEditable(team);
        // An invitation from this team already reserves the student's slot, so it is not counted twice.
        List<TeamInvitation> ownInvitation = invitations.findByTeamIdAndStatus(team.getId(), InvitationStatus.PENDING).stream()
                .filter(i -> i.getInvitee().getId().equals(student.id())).toList();
        long used = members.countByTeamId(team.getId())
                + invitations.countByTeamIdAndStatus(team.getId(), InvitationStatus.PENDING) - ownInvitation.size();
        if (used >= p.getMaxTeamSize()) {
            throw ApiException.conflict("TEAM_FULL", "The team is full (maximum " + p.getMaxTeamSize()
                    + " including pending invitations).");
        }
        User me = users.findById(student.id()).orElseThrow();
        List<User> existing = currentMembers(team);
        members.save(new TeamMember(team, me));
        ownInvitation.forEach(i -> i.resolve(InvitationStatus.ACCEPTED));
        cancelPendingInvitationsFor(p, student.id());
        notifications.notifyAll(existing, NotificationType.TEAM_UPDATE, me.getFullName() + " joined " + team.getName(),
                me.getFullName() + " joined your team for " + p.getTitle() + " using the team code.",
                PageRefs.project(p.getId()));
        log(team, me, "MEMBER_JOINED", me.getFullName() + " joined with the team code");
        return toDto(team);
    }

    /** Any member can replace the code (e.g. if it was shared too widely); the old code stops working. */
    @Transactional
    public TeamDto regenerateJoinCode(Long teamId, AuthUser student) {
        Team team = lockTeam(teamId);
        requireCurrentMember(team, student);
        if (team.getProject().getTeamFormation() != TeamFormation.STUDENT_INVITE) {
            throw ApiException.forbidden("Your teacher assigns teams for this project.");
        }
        ClassroomAccess.requireNotArchived(team.getProject().getClassroom());
        ensureMembershipEditable(team);
        team.setJoinCode(JoinCodes.generate(teams::existsByJoinCode));
        User me = users.findById(student.id()).orElseThrow();
        log(team, me, "JOIN_CODE_REGENERATED", me.getFullName() + " generated a new team code");
        return toDto(team);
    }

    // ------------------------------------------------------------------ invitations

    @Transactional
    public InvitationDto invite(Long teamId, String rawEmail, AuthUser student) {
        Team team = lockTeam(teamId);
        Project p = team.getProject();
        requireCurrentMember(team, student);
        ClassroomAccess.requireNotArchived(p.getClassroom());
        if (p.getTeamFormation() != TeamFormation.STUDENT_INVITE) {
            throw ApiException.forbidden("Your teacher assigns teams for this project.");
        }
        ensureMembershipEditable(team);
        ApiException notEligible = ApiException.badRequest("INVITEE_NOT_ELIGIBLE",
                "No verified student with that email is enrolled in this class.");
        User invitee = users.findByEmail(Texts.normalizeEmail(rawEmail))
                .filter(u -> u.getRole() == Role.STUDENT && u.isEmailVerified() && u.isActive())
                .orElseThrow(() -> notEligible);
        if (!access.isEnrolled(p.getClassroom(), invitee.getId())) {
            throw notEligible;
        }
        if (invitee.getId().equals(student.id())) {
            throw ApiException.badRequest("SELF_INVITE", "You are already in this team.");
        }
        if (members.findByProjectIdAndStudentId(p.getId(), invitee.getId()).isPresent()) {
            throw ApiException.conflict("INVITEE_IN_TEAM", invitee.getFullName() + " is already in a team for this project.");
        }
        if (invitations.existsByTeamIdAndInviteeIdAndStatus(teamId, invitee.getId(), InvitationStatus.PENDING)) {
            throw ApiException.conflict("ALREADY_INVITED", invitee.getFullName() + " already has a pending invitation from your team.");
        }
        ensureCapacity(team, 1);
        User inviter = users.getReferenceById(student.id());
        TeamInvitation inv = invitations.save(new TeamInvitation(team, invitee, inviter));
        User inviterUser = users.findById(student.id()).orElseThrow();
        notifications.notify(invitee, NotificationType.TEAM_INVITATION, "Team invitation: " + team.getName(),
                inviterUser.getFullName() + " invited you to join \"" + team.getName() + "\" for " + p.getTitle() + ".",
                PageRefs.project(p.getId()));
        log(team, inviter, "INVITATION_SENT", "Invited " + invitee.getFullName() + " (" + invitee.getEmail() + ")");
        return toDto(inv);
    }

    @Transactional
    public TeamDto accept(Long invitationId, AuthUser student) {
        TeamInvitation inv = requireOwnPendingInvitation(invitationId, student);
        Team team = lockTeam(inv.getTeam().getId());
        Project p = team.getProject();
        ClassroomAccess.requireNotArchived(p.getClassroom());
        if (!access.isEnrolled(p.getClassroom(), student.id())) {
            inv.resolve(InvitationStatus.CANCELLED);
            throw ApiException.conflict("NOT_ENROLLED", "You are no longer enrolled in this class.");
        }
        if (members.findByProjectIdAndStudentId(p.getId(), student.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_IN_TEAM", "You are already in a team for this project. Leave it first.");
        }
        ensureMembershipEditable(team);
        // The pending invitation already reserves a slot, so only current members are compared.
        if (members.countByTeamId(team.getId()) >= p.getMaxTeamSize()) {
            throw ApiException.conflict("TEAM_FULL", "This team is already full.");
        }
        User me = users.findById(student.id()).orElseThrow();
        List<User> existing = currentMembers(team);
        members.save(new TeamMember(team, me));
        inv.resolve(InvitationStatus.ACCEPTED);
        cancelPendingInvitationsFor(p, student.id());
        notifications.notifyAll(existing, NotificationType.INVITATION_RESPONSE, me.getFullName() + " joined " + team.getName(),
                me.getFullName() + " accepted the invitation and joined your team for " + p.getTitle() + ".",
                PageRefs.project(p.getId()));
        log(team, me, "MEMBER_JOINED", me.getFullName() + " accepted the invitation");
        return toDto(team);
    }

    @Transactional
    public void decline(Long invitationId, AuthUser student) {
        TeamInvitation inv = requireOwnPendingInvitation(invitationId, student);
        inv.resolve(InvitationStatus.DECLINED);
        User me = users.findById(student.id()).orElseThrow();
        Team team = inv.getTeam();
        notifications.notify(inv.getInvitedBy(), NotificationType.INVITATION_RESPONSE, "Invitation declined",
                me.getFullName() + " declined the invitation to join \"" + team.getName() + "\".",
                PageRefs.project(team.getProject().getId()));
        log(team, me, "INVITATION_DECLINED", me.getFullName() + " declined the invitation");
    }

    /** Any current team member (or the class teacher) can withdraw a pending invitation. */
    @Transactional
    public void cancelInvitation(Long invitationId, AuthUser user) {
        TeamInvitation inv = invitations.findById(invitationId).orElseThrow(() -> ApiException.notFound("Invitation"));
        Team team = inv.getTeam();
        if (user.isTeacher()) {
            access.requireOwned(team.getProject().getClassroom().getId(), user);
        } else {
            requireCurrentMember(team, user);
        }
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.conflict("INVITATION_CLOSED", "This invitation is no longer pending.");
        }
        inv.resolve(InvitationStatus.CANCELLED);
        notifications.notify(inv.getInvitee(), NotificationType.TEAM_UPDATE, "Invitation withdrawn",
                "The invitation to join \"" + team.getName() + "\" was withdrawn.", PageRefs.project(team.getProject().getId()));
        log(team, users.getReferenceById(user.id()), "INVITATION_CANCELLED", "Invitation to " + inv.getInvitee().getFullName() + " withdrawn");
    }

    // ------------------------------------------------------------------ membership changes

    @Transactional
    public void leave(Long teamId, AuthUser student) {
        Team team = lockTeam(teamId);
        TeamMember m = members.findByTeamIdAndStudentId(teamId, student.id())
                .orElseThrow(() -> ApiException.notFound("Team"));
        if (team.getProject().getTeamFormation() == TeamFormation.TEACHER_ASSIGNED) {
            throw ApiException.forbidden("Teams for this project are managed by your teacher. Ask them to change your team.");
        }
        ensureMembershipEditable(team);
        User me = m.getStudent();
        members.delete(m);
        members.flush();
        log(team, me, "MEMBER_LEFT", me.getFullName() + " left the team");
        List<User> remaining = currentMembers(team);
        notifications.notifyAll(remaining, NotificationType.TEAM_UPDATE, me.getFullName() + " left " + team.getName(),
                me.getFullName() + " left your team for " + team.getProject().getTitle() + ".",
                PageRefs.project(team.getProject().getId()));
        if (remaining.isEmpty()) {
            invitations.findByTeamIdAndStatus(teamId, InvitationStatus.PENDING).forEach(i -> i.resolve(InvitationStatus.CANCELLED));
            deleteIfUnused(team);
        }
    }

    @Transactional
    public TeamDto teacherAddMember(Long teamId, Long studentId, AuthUser teacher) {
        Team team = lockTeam(teamId);
        access.requireOwned(team.getProject().getClassroom().getId(), teacher);
        addMemberAsTeacher(team, studentId, users.getReferenceById(teacher.id()));
        return toDto(team);
    }

    @Transactional
    public TeamDto teacherRemoveMember(Long teamId, Long studentId, AuthUser teacher) {
        Team team = lockTeam(teamId);
        access.requireOwned(team.getProject().getClassroom().getId(), teacher);
        TeamMember m = members.findByTeamIdAndStudentId(teamId, studentId)
                .orElseThrow(() -> ApiException.notFound("Team member"));
        User student = m.getStudent();
        members.delete(m);
        log(team, users.getReferenceById(teacher.id()), "MEMBER_REMOVED", student.getFullName() + " removed by teacher");
        notifications.notify(student, NotificationType.TEAM_UPDATE, "Removed from " + team.getName(),
                "Your teacher removed you from \"" + team.getName() + "\" for " + team.getProject().getTitle() + ".",
                PageRefs.project(team.getProject().getId()));
        return toDto(team);
    }

    @Transactional
    public void deleteTeam(Long teamId, AuthUser teacher) {
        Team team = lockTeam(teamId);
        access.requireOwned(team.getProject().getClassroom().getId(), teacher);
        Submission sub = submissions.findByTeamId(teamId).orElse(null);
        if (sub != null && sub.getCurrentVersion() > 0) {
            throw ApiException.conflict("TEAM_HAS_SUBMISSION", "This team has submitted work; it cannot be deleted. Remove members instead.");
        }
        if (!workspaceFiles.findByTeamIdOrderByUploadedAtDesc(teamId).isEmpty()) {
            throw ApiException.conflict("TEAM_HAS_FILES", "This team has workspace files; it cannot be deleted. Remove members instead.");
        }
        List<User> current = currentMembers(team);
        notifications.notifyAll(current, NotificationType.TEAM_UPDATE, "Team deleted",
                "Your teacher deleted the team \"" + team.getName() + "\" for " + team.getProject().getTitle() + ".",
                PageRefs.project(team.getProject().getId()));
        hardDelete(team, sub);
    }

    /** Called when a teacher removes a student from a class. History is preserved in the activity log. */
    @Transactional
    public void detachStudentFromClass(Classroom c, User student, User actor) {
        for (TeamMember m : members.findByStudentInClass(student.getId(), c.getId())) {
            Team team = m.getTeam();
            members.delete(m);
            log(team, actor, "MEMBER_REMOVED", student.getFullName() + " removed (no longer enrolled in the class)");
        }
        invitations.findByInviteeInClass(student.getId(), c.getId(), InvitationStatus.PENDING)
                .forEach(i -> i.resolve(InvitationStatus.CANCELLED));
    }

    // ------------------------------------------------------------------ shared helpers (also used by workspace/submissions)

    public Team requireTeamForMember(Long teamId, AuthUser student) {
        Team team = teams.findById(teamId).orElseThrow(() -> ApiException.notFound("Team"));
        requireCurrentMember(team, student);
        return team;
    }

    /** Current member AND still enrolled in the class; otherwise the team is reported as not found. */
    public void requireCurrentMember(Team team, AuthUser user) {
        if (!user.isStudent() || !members.existsByTeamIdAndStudentId(team.getId(), user.id())
                || !access.isEnrolled(team.getProject().getClassroom(), user.id())) {
            throw ApiException.notFound("Team");
        }
    }

    public List<User> currentMembers(Team team) {
        return members.findByTeamIdOrderByJoinedAtAsc(team.getId()).stream().map(TeamMember::getStudent).toList();
    }

    public void log(Team team, User actor, String action, String details) {
        activities.save(new ActivityLog(team, actor, action, details));
    }

    public TeamDto toDto(Team team) {
        Project p = team.getProject();
        List<TeamMember> ms = members.findByTeamIdOrderByJoinedAtAsc(team.getId());
        Map<Long, UserSummary> summaries = profiles.summaryMap(ms.stream().map(TeamMember::getStudent).toList());
        List<MemberDto> memberDtos = ms.stream().map(m -> {
            UserSummary s = summaries.get(m.getStudent().getId());
            return new MemberDto(s.id(), s.fullName(), s.email(), s.rollNumber(), m.getJoinedAt());
        }).toList();
        List<InvitationDto> pending = invitations.findByTeamIdAndStatus(team.getId(), InvitationStatus.PENDING)
                .stream().map(this::toDto).toList();
        int slots = Math.max(0, p.getMaxTeamSize() - memberDtos.size() - pending.size());
        Submission sub = submissions.findByProjectIdAndTeamId(p.getId(), team.getId()).orElse(null);
        return new TeamDto(team.getId(), team.getName(), p.getId(), p.getTitle(), p.getMaxTeamSize(), memberDtos,
                pending, slots, views.summary(sub, p), team.getCreatedAt(), team.getJoinCode());
    }

    InvitationDto toDto(TeamInvitation i) {
        Project p = i.getProject();
        return new InvitationDto(i.getId(), i.getTeam().getId(), i.getTeam().getName(), p.getId(), p.getTitle(),
                p.getClassroom().displayName(), profiles.summary(i.getInvitee()), i.getInvitedBy().getFullName(),
                i.getStatus(), i.getCreatedAt(), i.getRespondedAt());
    }

    /** Access is checked before anything else, so outsiders get 404 and learn nothing about the project or its teams. */
    private Project requireGroupProject(Long projectId, AuthUser user) {
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        if (user.isTeacher()) {
            access.requireOwned(p.getClassroom().getId(), user);
        } else {
            requireEnrolled(p, user);
        }
        if (!p.isGroup()) {
            throw ApiException.badRequest("NOT_GROUP_PROJECT", "This is an individual project; teams are not used.");
        }
        return p;
    }

    private void requireEnrolled(Project p, AuthUser student) {
        if (!student.isStudent() || !access.isEnrolled(p.getClassroom(), student.id())) {
            throw ApiException.notFound("Project");
        }
    }

    private Team lockTeam(Long teamId) {
        return teams.findByIdForUpdate(teamId).orElseThrow(() -> ApiException.notFound("Team"));
    }

    private TeamInvitation requireOwnPendingInvitation(Long invitationId, AuthUser student) {
        TeamInvitation inv = invitations.findById(invitationId)
                .filter(i -> student.isStudent() && i.getInvitee().getId().equals(student.id()))
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.conflict("INVITATION_CLOSED", "This invitation is no longer pending.");
        }
        return inv;
    }

    /** Pending invitations reserve slots: members + pending + additional must fit. */
    private void ensureCapacity(Team team, int additional) {
        long used = members.countByTeamId(team.getId()) + invitations.countByTeamIdAndStatus(team.getId(), InvitationStatus.PENDING);
        if (used + additional > team.getProject().getMaxTeamSize()) {
            throw ApiException.conflict("TEAM_FULL", "The team is full (maximum " + team.getProject().getMaxTeamSize()
                    + " including pending invitations).");
        }
    }

    /** Students cannot change membership once the team's work has been finally evaluated or rejected. */
    private void ensureMembershipEditable(Team team) {
        submissions.findByTeamId(team.getId()).ifPresent(s -> {
            if (s.getStatus() == SubmissionStatus.EVALUATED || s.getStatus() == SubmissionStatus.REJECTED) {
                throw ApiException.conflict("TEAM_LOCKED", "Team membership is locked because the project has been evaluated.");
            }
        });
    }

    private void addMemberAsTeacher(Team team, Long studentId, User actor) {
        Project p = team.getProject();
        User student = users.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT && access.isEnrolled(p.getClassroom(), u.getId()))
                .orElseThrow(() -> ApiException.badRequest("STUDENT_NOT_ENROLLED", "That student is not enrolled in this class."));
        if (members.findByProjectIdAndStudentId(p.getId(), studentId).isPresent()) {
            throw ApiException.conflict("ALREADY_IN_TEAM", student.getFullName() + " is already in a team for this project.");
        }
        ensureCapacity(team, 1);
        members.save(new TeamMember(team, student));
        cancelPendingInvitationsFor(p, studentId);
        log(team, actor, "MEMBER_ADDED", student.getFullName() + " added by teacher");
        notifications.notify(student, NotificationType.TEAM_UPDATE, "Added to " + team.getName(),
                "Your teacher added you to \"" + team.getName() + "\" for " + p.getTitle() + ".", PageRefs.project(p.getId()));
    }

    /** Student-created teams get a join code; teacher-assigned teams are managed by the teacher and have none. */
    private Team newTeam(Project p, String name, User creator) {
        Team team = new Team(p, name, creator);
        if (p.getTeamFormation() == TeamFormation.STUDENT_INVITE) {
            team.setJoinCode(JoinCodes.generate(teams::existsByJoinCode));
        }
        return team;
    }

    private void cancelPendingInvitationsFor(Project p, Long studentId) {
        invitations.findByProjectIdAndInviteeIdAndStatus(p.getId(), studentId, InvitationStatus.PENDING)
                .forEach(i -> i.resolve(InvitationStatus.CANCELLED));
    }

    private void deleteIfUnused(Team team) {
        Submission sub = submissions.findByTeamId(team.getId()).orElse(null);
        boolean hasFiles = !workspaceFiles.findByTeamIdOrderByUploadedAtDesc(team.getId()).isEmpty();
        if ((sub == null || sub.getCurrentVersion() == 0) && !hasFiles) {
            hardDelete(team, sub);
        }
    }

    private void hardDelete(Team team, Submission sub) {
        if (sub != null) {
            submissions.delete(sub);
        }
        invitations.deleteAll(invitations.findByTeamIdOrderByCreatedAtDesc(team.getId()));
        members.deleteAll(members.findByTeamIdOrderByJoinedAtAsc(team.getId()));
        // Deleted through JPA rather than left to ON DELETE CASCADE: entries logged earlier in this transaction
        // (e.g. "left the team") are still managed and would otherwise reference the deleted team at flush.
        activities.deleteAll(activities.findByTeamId(team.getId()));
        teams.flush();
        teams.delete(team);
    }
}
