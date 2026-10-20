package com.collabboard.team;

import com.collabboard.auth.AuthUser;
import com.collabboard.classroom.ClassroomAccess;
import com.collabboard.common.ApiException;
import com.collabboard.common.Texts;
import com.collabboard.project.Project;
import com.collabboard.storage.FileContextType;
import com.collabboard.storage.FileDto;
import com.collabboard.storage.StorageService;
import com.collabboard.storage.StoredFile;
import com.collabboard.submission.Submission;
import com.collabboard.submission.SubmissionRepository;
import com.collabboard.submission.SubmissionStatus;
import com.collabboard.submission.SubmissionViews;
import com.collabboard.team.TeamDtos.ActivityDto;
import com.collabboard.team.TeamDtos.DetailsRequest;
import com.collabboard.team.TeamDtos.WorkspaceFileDto;
import com.collabboard.team.TeamDtos.WorkspaceView;
import com.collabboard.user.User;
import com.collabboard.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Shared group-project workspace. Every current member has exactly the same permissions:
 * upload, replace, delete, restore files and edit the GitHub URL / notes.
 */
@Service
public class WorkspaceService {

    private final TeamRepository teams;
    private final WorkspaceFileRepository workspaceFiles;
    private final ActivityLogRepository activities;
    private final SubmissionRepository submissions;
    private final UserRepository users;
    private final StorageService storage;
    private final TeamService teamService;
    private final ClassroomAccess access;
    private final SubmissionViews views;

    public WorkspaceService(TeamRepository teams, WorkspaceFileRepository workspaceFiles,
                            ActivityLogRepository activities, SubmissionRepository submissions, UserRepository users,
                            StorageService storage, TeamService teamService, ClassroomAccess access,
                            SubmissionViews views) {
        this.teams = teams;
        this.workspaceFiles = workspaceFiles;
        this.activities = activities;
        this.submissions = submissions;
        this.users = users;
        this.storage = storage;
        this.teamService = teamService;
        this.access = access;
        this.views = views;
    }

    /** Members get an editable view; the class teacher gets a read-only view (including draft evaluations). */
    @Transactional(readOnly = true)
    public WorkspaceView view(Long teamId, AuthUser user) {
        Team team = teams.findById(teamId).orElseThrow(() -> ApiException.notFound("Team"));
        boolean teacher = access.isTeacherOf(team.getProject().getClassroom(), user);
        if (!teacher) {
            teamService.requireCurrentMember(team, user);
        }
        Project p = team.getProject();
        Submission sub = submissions.findByProjectIdAndTeamId(p.getId(), teamId).orElse(null);
        List<WorkspaceFileDto> all = workspaceFiles.findByTeamIdOrderByUploadedAtDesc(teamId).stream().map(this::toDto).toList();
        List<ActivityDto> log = activities.findTop100ByTeamIdOrderByCreatedAtDescIdDesc(teamId).stream()
                .map(a -> new ActivityDto(a.getId(), a.getActor().getFullName(), a.getAction(), a.getDetails(), a.getCreatedAt()))
                .toList();
        return new WorkspaceView(teamService.toDto(team), !teacher && isEditable(sub), team.getGithubUrl(), team.getNotes(),
                team.getNotesUpdatedBy() == null ? null : team.getNotesUpdatedBy().getFullName(), team.getNotesUpdatedAt(),
                all.stream().filter(f -> f.status() == WorkspaceFileStatus.ACTIVE).toList(),
                all.stream().filter(f -> f.status() != WorkspaceFileStatus.ACTIVE).toList(),
                log, views.studentView(p, sub),
                List.copyOf(Texts.parseExtensions(p.getAllowedExtensions())), p.getMaxFileSizeMb());
    }

    @Transactional
    public WorkspaceFileDto upload(Long teamId, MultipartFile file, WorkspaceCategory category, Long replaceId, AuthUser user) {
        Team team = editableTeam(teamId, user);
        User actor = users.getReferenceById(user.id());
        WorkspaceFile replaced = null;
        if (replaceId != null) {
            replaced = workspaceFiles.findByIdAndTeamId(replaceId, teamId)
                    .filter(f -> f.getStatus() == WorkspaceFileStatus.ACTIVE)
                    .orElseThrow(() -> ApiException.notFound("Workspace file"));
        }
        StoredFile stored = storage.store(file, team.getProject(), FileContextType.TEAM_WORKSPACE, teamId, actor);
        WorkspaceCategory cat = category != null ? category : replaced != null ? replaced.getCategory() : WorkspaceCategory.OTHER;
        WorkspaceFile wf = workspaceFiles.save(new WorkspaceFile(team, stored, cat, replaced, actor));
        String who = users.findById(user.id()).map(User::getFullName).orElse("A member");
        if (replaced != null) {
            replaced.markRemoved(WorkspaceFileStatus.REPLACED, actor);
            teamService.log(team, actor, "FILE_REPLACED", who + " replaced \"" + replaced.getFile().getOriginalName()
                    + "\" with \"" + stored.getOriginalName() + "\"");
        } else {
            teamService.log(team, actor, "FILE_UPLOADED", who + " uploaded \"" + stored.getOriginalName() + "\" (" + cat + ")");
        }
        return toDto(wf);
    }

    @Transactional
    public void delete(Long teamId, Long workspaceFileId, AuthUser user) {
        Team team = editableTeam(teamId, user);
        WorkspaceFile wf = workspaceFiles.findByIdAndTeamId(workspaceFileId, teamId)
                .filter(f -> f.getStatus() == WorkspaceFileStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Workspace file"));
        User actor = users.findById(user.id()).orElseThrow();
        wf.markRemoved(WorkspaceFileStatus.DELETED, actor);
        teamService.log(team, actor, "FILE_DELETED", actor.getFullName() + " deleted \"" + wf.getFile().getOriginalName() + "\"");
    }

    /** Deleted files are kept and can be restored by any member. */
    @Transactional
    public WorkspaceFileDto restore(Long teamId, Long workspaceFileId, AuthUser user) {
        Team team = editableTeam(teamId, user);
        WorkspaceFile wf = workspaceFiles.findByIdAndTeamId(workspaceFileId, teamId)
                .filter(f -> f.getStatus() == WorkspaceFileStatus.DELETED)
                .orElseThrow(() -> ApiException.notFound("Deleted workspace file"));
        User actor = users.findById(user.id()).orElseThrow();
        wf.restore();
        teamService.log(team, actor, "FILE_RESTORED", actor.getFullName() + " restored \"" + wf.getFile().getOriginalName() + "\"");
        return toDto(wf);
    }

    @Transactional
    public void updateDetails(Long teamId, DetailsRequest req, AuthUser user) {
        Team team = editableTeam(teamId, user);
        User actor = users.findById(user.id()).orElseThrow();
        String url = Texts.normalizeGithubUrl(req.githubUrl());
        String notes = Texts.trimToNull(req.notes());
        if (!Objects.equals(url, team.getGithubUrl())) {
            team.setGithubUrl(url);
            teamService.log(team, actor, "GITHUB_UPDATED", actor.getFullName()
                    + (url == null ? " removed the GitHub URL" : " set the GitHub URL to " + url));
        }
        if (!Objects.equals(notes, team.getNotes())) {
            team.setNotes(notes);
            team.setNotesUpdatedBy(actor);
            team.setNotesUpdatedAt(Instant.now());
            teamService.log(team, actor, "NOTES_UPDATED", actor.getFullName() + " updated the project notes");
        }
    }

    private Team editableTeam(Long teamId, AuthUser user) {
        Team team = teamService.requireTeamForMember(teamId, user);
        ClassroomAccess.requireNotArchived(team.getProject().getClassroom());
        Submission sub = submissions.findByProjectIdAndTeamId(team.getProject().getId(), teamId).orElse(null);
        if (!isEditable(sub)) {
            throw ApiException.conflict("WORKSPACE_LOCKED", "The workspace is locked because the project has been evaluated.");
        }
        return team;
    }

    private static boolean isEditable(Submission sub) {
        return sub == null || (sub.getStatus() != SubmissionStatus.EVALUATED && sub.getStatus() != SubmissionStatus.REJECTED);
    }

    private WorkspaceFileDto toDto(WorkspaceFile f) {
        return new WorkspaceFileDto(f.getId(), f.getCategory(), f.getStatus(), FileDto.of(f.getFile()),
                f.getUploadedBy().getFullName(), f.getUploadedAt(),
                f.getRemovedBy() == null ? null : f.getRemovedBy().getFullName(), f.getRemovedAt(),
                f.getReplaces() == null ? null : f.getReplaces().getId());
    }
}
