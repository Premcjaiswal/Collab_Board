package com.collabboard.team;

import com.collabboard.auth.CurrentUser;
import com.collabboard.team.TeamDtos.CreateTeamRequest;
import com.collabboard.team.TeamDtos.DetailsRequest;
import com.collabboard.team.TeamDtos.InvitationDto;
import com.collabboard.team.TeamDtos.InviteRequest;
import com.collabboard.team.TeamDtos.JoinTeamRequest;
import com.collabboard.team.TeamDtos.MemberRequest;
import com.collabboard.team.TeamDtos.MyTeamResponse;
import com.collabboard.team.TeamDtos.TeamDto;
import com.collabboard.team.TeamDtos.WorkspaceFileDto;
import com.collabboard.team.TeamDtos.WorkspaceView;
import com.collabboard.user.ProfileDtos.UserSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class TeamController {

    private final TeamService teams;
    private final WorkspaceService workspace;
    private final CurrentUser currentUser;

    public TeamController(TeamService teams, WorkspaceService workspace, CurrentUser currentUser) {
        this.teams = teams;
        this.workspace = workspace;
        this.currentUser = currentUser;
    }

    // ---- teams of a project

    @GetMapping("/api/projects/{projectId}/teams")
    @PreAuthorize("hasRole('TEACHER')")
    public List<TeamDto> listTeams(@PathVariable Long projectId) {
        return teams.listTeams(projectId, currentUser.requireTeacher());
    }

    @GetMapping("/api/projects/{projectId}/my-team")
    @PreAuthorize("hasRole('STUDENT')")
    public MyTeamResponse myTeam(@PathVariable Long projectId) {
        return teams.myTeam(projectId, currentUser.requireStudent());
    }

    @PostMapping("/api/projects/{projectId}/teams")
    public ResponseEntity<TeamDto> createTeam(@PathVariable Long projectId, @Valid @RequestBody CreateTeamRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teams.createTeam(projectId, req, currentUser.get()));
    }

    @PostMapping("/api/projects/{projectId}/teams/join")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamDto joinWithCode(@PathVariable Long projectId, @Valid @RequestBody JoinTeamRequest req) {
        return teams.joinByCode(projectId, req.code(), currentUser.get());
    }

    @PostMapping("/api/teams/{teamId}/join-code")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamDto regenerateJoinCode(@PathVariable Long teamId) {
        return teams.regenerateJoinCode(teamId, currentUser.get());
    }

    @GetMapping("/api/projects/{projectId}/eligible-students")
    public List<UserSummary> eligible(@PathVariable Long projectId) {
        return teams.eligibleStudents(projectId, currentUser.get());
    }

    // ---- invitations

    @PostMapping("/api/teams/{teamId}/invitations")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<InvitationDto> invite(@PathVariable Long teamId, @Valid @RequestBody InviteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teams.invite(teamId, req.email(), currentUser.requireStudent()));
    }

    @GetMapping("/api/invitations")
    @PreAuthorize("hasRole('STUDENT')")
    public List<InvitationDto> myInvitations() {
        return teams.myInvitations(currentUser.requireStudent());
    }

    @PostMapping("/api/invitations/{id}/accept")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamDto accept(@PathVariable Long id) {
        return teams.accept(id, currentUser.requireStudent());
    }

    @PostMapping("/api/invitations/{id}/decline")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> decline(@PathVariable Long id) {
        teams.decline(id, currentUser.requireStudent());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/invitations/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        teams.cancelInvitation(id, currentUser.get());
        return ResponseEntity.noContent().build();
    }

    // ---- membership

    @PostMapping("/api/teams/{teamId}/leave")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> leave(@PathVariable Long teamId) {
        teams.leave(teamId, currentUser.requireStudent());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/teams/{teamId}/members")
    @PreAuthorize("hasRole('TEACHER')")
    public TeamDto addMember(@PathVariable Long teamId, @Valid @RequestBody MemberRequest req) {
        return teams.teacherAddMember(teamId, req.studentId(), currentUser.requireTeacher());
    }

    @DeleteMapping("/api/teams/{teamId}/members/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public TeamDto removeMember(@PathVariable Long teamId, @PathVariable Long studentId) {
        return teams.teacherRemoveMember(teamId, studentId, currentUser.requireTeacher());
    }

    @DeleteMapping("/api/teams/{teamId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long teamId) {
        teams.deleteTeam(teamId, currentUser.requireTeacher());
        return ResponseEntity.noContent().build();
    }

    // ---- shared workspace

    @GetMapping("/api/teams/{teamId}/workspace")
    public WorkspaceView workspace(@PathVariable Long teamId) {
        return workspace.view(teamId, currentUser.get());
    }

    @PostMapping(value = "/api/teams/{teamId}/files", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<WorkspaceFileDto> upload(@PathVariable Long teamId,
                                                   @RequestPart("file") MultipartFile file,
                                                   @RequestParam(value = "category", required = false) WorkspaceCategory category,
                                                   @RequestParam(value = "replaceId", required = false) Long replaceId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workspace.upload(teamId, file, category, replaceId, currentUser.requireStudent()));
    }

    @DeleteMapping("/api/teams/{teamId}/files/{fileId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> deleteFile(@PathVariable Long teamId, @PathVariable Long fileId) {
        workspace.delete(teamId, fileId, currentUser.requireStudent());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/teams/{teamId}/files/{fileId}/restore")
    @PreAuthorize("hasRole('STUDENT')")
    public WorkspaceFileDto restoreFile(@PathVariable Long teamId, @PathVariable Long fileId) {
        return workspace.restore(teamId, fileId, currentUser.requireStudent());
    }

    @PutMapping("/api/teams/{teamId}/details")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> updateDetails(@PathVariable Long teamId, @Valid @RequestBody DetailsRequest req) {
        workspace.updateDetails(teamId, req, currentUser.requireStudent());
        return ResponseEntity.noContent().build();
    }
}
