package com.collabboard.storage;

import com.collabboard.auth.AuthUser;
import com.collabboard.auth.CurrentUser;
import com.collabboard.classroom.Classroom;
import com.collabboard.classroom.ClassroomAccess;
import com.collabboard.common.ApiException;
import com.collabboard.submission.SubmissionRepository;
import com.collabboard.team.Team;
import com.collabboard.team.TeamMemberRepository;
import com.collabboard.team.TeamRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Authorized file download. Every request re-checks that the caller may see the file's context:
 * the owning student / current team members, or the teacher of the class.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final StoredFileRepository files;
    private final StorageService storage;
    private final SubmissionRepository submissions;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final ClassroomAccess classroomAccess;
    private final CurrentUser currentUser;

    public FileController(StoredFileRepository files, StorageService storage, SubmissionRepository submissions,
                          TeamRepository teams, TeamMemberRepository members, ClassroomAccess classroomAccess,
                          CurrentUser currentUser) {
        this.files = files;
        this.storage = storage;
        this.submissions = submissions;
        this.teams = teams;
        this.members = members;
        this.classroomAccess = classroomAccess;
        this.currentUser = currentUser;
    }

    @GetMapping("/{id}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        AuthUser user = currentUser.get();
        StoredFile file = files.findById(id).orElseThrow(() -> ApiException.notFound("File"));
        if (!canRead(file, user)) {
            throw ApiException.notFound("File");
        }
        Resource resource = storage.load(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.getSizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    boolean canRead(StoredFile file, AuthUser user) {
        return switch (file.getContextType()) {
            case SUBMISSION -> submissions.findById(file.getContextId()).map(s -> {
                Classroom c = s.item().getClassroom();
                if (classroomAccess.isTeacherOf(c, user)) {
                    return true;
                }
                return user.isStudent() && s.getStudent() != null && s.getStudent().getId().equals(user.id())
                        && classroomAccess.isEnrolled(c, user.id());
            }).orElse(false);
            case TEAM_WORKSPACE -> teams.findById(file.getContextId()).map(t -> canReadTeam(t, user)).orElse(false);
        };
    }

    private boolean canReadTeam(Team team, AuthUser user) {
        Classroom c = team.getProject().getClassroom();
        if (classroomAccess.isTeacherOf(c, user)) {
            return true;
        }
        return user.isStudent() && members.existsByTeamIdAndStudentId(team.getId(), user.id())
                && classroomAccess.isEnrolled(c, user.id());
    }
}
