package com.collabboard.evaluation;

import com.collabboard.auth.CurrentUser;
import com.collabboard.evaluation.EvaluationDtos.EvaluateRequest;
import com.collabboard.evaluation.EvaluationDtos.ItemKind;
import com.collabboard.evaluation.EvaluationDtos.SubmissionDetail;
import com.collabboard.evaluation.EvaluationDtos.SubmissionRow;
import com.collabboard.submission.SubmissionDtos.EvaluationDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/evaluation")
@PreAuthorize("hasRole('TEACHER')")
public class EvaluationController {

    private final EvaluationService service;
    private final CurrentUser currentUser;

    public EvaluationController(EvaluationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/submissions")
    public List<SubmissionRow> list(@RequestParam(required = false) Long classId,
                                    @RequestParam(required = false) ItemKind kind,
                                    @RequestParam(required = false) Long itemId,
                                    @RequestParam(required = false) String status) {
        return service.list(currentUser.requireTeacher(), classId, kind, itemId, status);
    }

    @GetMapping("/submissions/{id}")
    public SubmissionDetail detail(@PathVariable Long id) {
        return service.detail(id, currentUser.requireTeacher());
    }

    @PostMapping("/submissions/{id}/evaluations")
    public EvaluationDto evaluate(@PathVariable Long id, @Valid @RequestBody EvaluateRequest req) {
        return service.evaluate(id, req, currentUser.requireTeacher());
    }

    @PostMapping("/evaluations/{id}/publish")
    public EvaluationDto publish(@PathVariable Long id) {
        return service.publish(id, currentUser.requireTeacher());
    }
}
