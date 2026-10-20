package com.collabboard.assignment;

import com.collabboard.assignment.AssignmentDtos.AssignmentRequest;
import com.collabboard.assignment.AssignmentDtos.AssignmentView;
import com.collabboard.auth.CurrentUser;
import com.collabboard.submission.SubmissionDtos.SubmissionView;
import com.collabboard.submission.SubmissionService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService service;
    private final SubmissionService submissions;
    private final CurrentUser currentUser;

    public AssignmentController(AssignmentService service, SubmissionService submissions, CurrentUser currentUser) {
        this.service = service;
        this.submissions = submissions;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AssignmentView> list(@RequestParam(required = false) Long classId) {
        return service.list(currentUser.get(), classId);
    }

    @GetMapping("/{id}")
    public AssignmentView get(@PathVariable Long id) {
        return service.get(id, currentUser.get());
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AssignmentView> create(@Valid @RequestBody AssignmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, currentUser.requireTeacher()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public AssignmentView update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest req) {
        return service.update(id, req, currentUser.requireTeacher());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id, currentUser.requireTeacher());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/my-submission")
    @PreAuthorize("hasRole('STUDENT')")
    public SubmissionView mySubmission(@PathVariable Long id) {
        return submissions.myAssignmentSubmission(id, currentUser.requireStudent());
    }

    @PostMapping(value = "/{id}/submissions", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionView> submit(@PathVariable Long id,
                                                 @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                                 @RequestParam(value = "notes", required = false) String notes) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissions.submitAssignment(id, files, notes, currentUser.requireStudent()));
    }
}
