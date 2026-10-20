package com.collabboard.submission;

import com.collabboard.auth.CurrentUser;
import com.collabboard.submission.SubmissionDtos.SubmissionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamSubmissionController {

    public record TeamSubmitRequest(@Size(max = 5000) String notes) {
    }

    private final SubmissionService submissions;
    private final CurrentUser currentUser;

    public TeamSubmissionController(SubmissionService submissions, CurrentUser currentUser) {
        this.submissions = submissions;
        this.currentUser = currentUser;
    }

    /** Any current member of the team may submit the final project. */
    @PostMapping("/api/teams/{teamId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionView> submit(@PathVariable Long teamId,
                                                 @Valid @RequestBody(required = false) TeamSubmitRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissions.submitTeam(teamId, req == null ? null : req.notes(), currentUser.requireStudent()));
    }
}
