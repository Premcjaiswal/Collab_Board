package com.collabboard.submission;

import com.collabboard.assignment.Assignment;
import com.collabboard.assignment.AssignmentService;
import com.collabboard.auth.AuthUser;
import com.collabboard.classroom.ClassroomAccess;
import com.collabboard.common.ApiException;
import com.collabboard.common.PageRefs;
import com.collabboard.common.Texts;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.project.Project;
import com.collabboard.project.ProjectService;
import com.collabboard.storage.FileContextType;
import com.collabboard.storage.StorageService;
import com.collabboard.storage.StoredFile;
import com.collabboard.submission.SubmissionDtos.SubmissionView;
import com.collabboard.team.Team;
import com.collabboard.team.TeamMember;
import com.collabboard.team.TeamMemberRepository;
import com.collabboard.team.TeamRepository;
import com.collabboard.team.TeamService;
import com.collabboard.team.WorkspaceFile;
import com.collabboard.team.WorkspaceFileRepository;
import com.collabboard.team.WorkspaceFileStatus;
import com.collabboard.user.User;
import com.collabboard.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * Creates submissions and their immutable versions. Every (re)submission is a new version; nothing is overwritten.
 * Deadline/resubmission rules come from {@link SubmissionPolicy}.
 */
@Service
public class SubmissionService {

    public static final int MAX_FILES_PER_SUBMISSION = 10;

    private final SubmissionRepository submissions;
    private final SubmissionVersionRepository versions;
    private final AssignmentService assignmentService;
    private final ProjectService projectService;
    private final TeamService teamService;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final WorkspaceFileRepository workspaceFiles;
    private final StorageService storage;
    private final UserRepository users;
    private final NotificationService notifications;
    private final SubmissionViews views;

    public SubmissionService(SubmissionRepository submissions, SubmissionVersionRepository versions,
                             AssignmentService assignmentService, ProjectService projectService,
                             TeamService teamService, TeamRepository teams, TeamMemberRepository members,
                             WorkspaceFileRepository workspaceFiles, StorageService storage, UserRepository users,
                             NotificationService notifications, SubmissionViews views) {
        this.submissions = submissions;
        this.versions = versions;
        this.assignmentService = assignmentService;
        this.projectService = projectService;
        this.teamService = teamService;
        this.teams = teams;
        this.members = members;
        this.workspaceFiles = workspaceFiles;
        this.storage = storage;
        this.users = users;
        this.notifications = notifications;
        this.views = views;
    }

    // ------------------------------------------------------------------ assignments (always individual)

    @Transactional(readOnly = true)
    public SubmissionView myAssignmentSubmission(Long assignmentId, AuthUser student) {
        Assignment a = assignmentService.requireVisible(assignmentId, student);
        requireStudent(student);
        return views.studentView(a, submissions.findByAssignmentIdAndStudentId(a.getId(), student.id()).orElse(null));
    }

    @Transactional
    public SubmissionView submitAssignment(Long assignmentId, List<MultipartFile> files, String notes, AuthUser student) {
        requireStudent(student);
        Assignment a = assignmentService.requireVisible(assignmentId, student);
        User me = users.findById(student.id()).orElseThrow();
        Submission s = submissions.findByAssignmentIdAndStudentId(a.getId(), me.getId())
                .orElseGet(() -> submissions.save(Submission.forAssignment(a, me)));
        List<MultipartFile> nonEmpty = nonEmpty(files);
        if (nonEmpty.isEmpty()) {
            throw ApiException.badRequest("FILE_REQUIRED", "Attach at least one file to submit.");
        }
        createVersion(s, a, me, nonEmpty, null, notes);
        return views.studentView(a, s);
    }

    // ------------------------------------------------------------------ individual projects

    @Transactional(readOnly = true)
    public SubmissionView myProjectSubmission(Long projectId, AuthUser student) {
        requireStudent(student);
        Project p = projectService.requireVisible(projectId, student);
        if (p.isGroup()) {
            Submission s = members.findByProjectIdAndStudentId(projectId, student.id())
                    .flatMap(m -> submissions.findByProjectIdAndTeamId(projectId, m.getTeam().getId()))
                    .orElse(null);
            return views.studentView(p, s);
        }
        return views.studentView(p, submissions.findByProjectIdAndStudentId(projectId, student.id()).orElse(null));
    }

    @Transactional
    public SubmissionView submitIndividualProject(Long projectId, List<MultipartFile> files, String githubUrl,
                                                  String notes, AuthUser student) {
        requireStudent(student);
        Project p = projectService.requireVisible(projectId, student);
        if (p.isGroup()) {
            throw ApiException.badRequest("GROUP_PROJECT", "This is a group project. Submit from your team workspace.");
        }
        User me = users.findById(student.id()).orElseThrow();
        String url = Texts.normalizeGithubUrl(githubUrl);
        List<MultipartFile> nonEmpty = nonEmpty(files);
        if (nonEmpty.isEmpty() && url == null) {
            throw ApiException.badRequest("FILE_REQUIRED", "Attach at least one file or provide a GitHub repository URL.");
        }
        Submission s = submissions.findByProjectIdAndStudentId(projectId, me.getId())
                .orElseGet(() -> submissions.save(Submission.forIndividualProject(p, me)));
        createVersion(s, p, me, nonEmpty, url, notes);
        return views.studentView(p, s);
    }

    // ------------------------------------------------------------------ group projects

    /**
     * Any current team member may submit. The version snapshots the workspace's active files,
     * GitHub URL and notes at this moment.
     */
    @Transactional
    public SubmissionView submitTeam(Long teamId, String notes, AuthUser student) {
        requireStudent(student);
        Team team = teams.findByIdForUpdate(teamId).orElseThrow(() -> ApiException.notFound("Team"));
        teamService.requireCurrentMember(team, student);
        Project p = team.getProject();
        ClassroomAccess.requireNotArchived(p.getClassroom());
        User me = users.findById(student.id()).orElseThrow();
        Submission s = submissions.findByProjectIdAndTeamId(p.getId(), teamId)
                .orElseGet(() -> submissions.save(Submission.forTeam(p, team)));
        SubmissionPolicy.Decision d = SubmissionPolicy.evaluate(p, s.getStatus(), s.getCurrentVersion(), Instant.now());
        if (!d.allowed()) {
            throw ApiException.conflict("SUBMISSION_CLOSED", d.reason());
        }
        List<StoredFile> files = workspaceFiles
                .findByTeamIdAndStatusOrderByCategoryAscUploadedAtDesc(teamId, WorkspaceFileStatus.ACTIVE)
                .stream().map(WorkspaceFile::getFile).toList();
        if (files.isEmpty() && team.getGithubUrl() == null) {
            throw ApiException.badRequest("FILE_REQUIRED", "Upload at least one file or add a GitHub URL to the workspace before submitting.");
        }
        String versionNotes = Texts.trimToNull(notes) != null ? notes.trim() : team.getNotes();
        SubmissionVersion v = new SubmissionVersion(s, s.getCurrentVersion() + 1, me, d.late(), team.getGithubUrl(), versionNotes);
        v.getFiles().addAll(files);
        versions.save(v);
        markSubmitted(s, v, d.late());
        teamService.log(team, me, "PROJECT_SUBMITTED", me.getFullName() + " submitted version " + v.getVersionNo()
                + (d.late() ? " (late)" : "") + " with " + files.size() + " file(s)");
        List<User> teamMembers = members.findByTeamIdOrderByJoinedAtAsc(teamId).stream().map(TeamMember::getStudent).toList();
        notifications.notifyAll(teamMembers, NotificationType.SUBMISSION, "Project submitted: " + p.getTitle(),
                me.getFullName() + " submitted version " + v.getVersionNo() + " for team \"" + team.getName() + "\""
                        + (d.late() ? " after the deadline (late)." : "."), PageRefs.project(p.getId()));
        return views.studentView(p, s);
    }

    // ------------------------------------------------------------------ internals

    private void createVersion(Submission s, WorkItem item, User me, List<MultipartFile> uploads, String githubUrl, String notes) {
        ClassroomAccess.requireNotArchived(item.getClassroom());
        SubmissionPolicy.Decision d = SubmissionPolicy.evaluate(item, s.getStatus(), s.getCurrentVersion(), Instant.now());
        if (!d.allowed()) {
            throw ApiException.conflict("SUBMISSION_CLOSED", d.reason());
        }
        if (uploads.size() > MAX_FILES_PER_SUBMISSION) {
            throw ApiException.badRequest("TOO_MANY_FILES", "You can attach at most " + MAX_FILES_PER_SUBMISSION + " files.");
        }
        String trimmedNotes = Texts.trimToNull(notes);
        if (trimmedNotes != null && trimmedNotes.length() > 5000) {
            throw ApiException.badRequest("NOTES_TOO_LONG", "Notes can be at most 5000 characters.");
        }
        SubmissionVersion v = new SubmissionVersion(s, s.getCurrentVersion() + 1, me, d.late(), githubUrl, trimmedNotes);
        for (MultipartFile f : uploads) {
            v.getFiles().add(storage.store(f, item, FileContextType.SUBMISSION, s.getId(), me));
        }
        versions.save(v);
        markSubmitted(s, v, d.late());
        String link = s.isAssignment() ? PageRefs.assignment(item.getId()) : PageRefs.project(item.getId());
        notifications.notify(me, NotificationType.SUBMISSION, "Submission received: " + item.getTitle(),
                "Version " + v.getVersionNo() + " submitted with " + v.getFiles().size() + " file(s)"
                        + (d.late() ? " after the deadline (marked late)." : "."), link);
    }

    private void markSubmitted(Submission s, SubmissionVersion v, boolean late) {
        s.setCurrentVersion(v.getVersionNo());
        s.setLastSubmittedAt(v.getSubmittedAt());
        s.setStatus(late ? SubmissionStatus.LATE : SubmissionStatus.SUBMITTED);
    }

    private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    private static void requireStudent(AuthUser user) {
        if (!user.isStudent()) {
            throw ApiException.forbidden("Only students can submit work.");
        }
    }
}
