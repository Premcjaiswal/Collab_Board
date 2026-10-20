package com.collabboard.classroom;

import com.collabboard.auth.CurrentUser;
import com.collabboard.classroom.ClassroomDtos.ArchiveRequest;
import com.collabboard.classroom.ClassroomDtos.ClassRequest;
import com.collabboard.classroom.ClassroomDtos.ClassSummary;
import com.collabboard.classroom.ClassroomDtos.JoinRequest;
import com.collabboard.classroom.ClassroomDtos.RosterEntry;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
public class ClassroomController {

    private final ClassroomService service;
    private final CurrentUser currentUser;

    public ClassroomController(ClassroomService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<ClassSummary> list() {
        return service.list(currentUser.get());
    }

    @GetMapping("/{id}")
    public ClassSummary get(@PathVariable Long id) {
        return service.get(id, currentUser.get());
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassSummary> create(@Valid @RequestBody ClassRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, currentUser.requireTeacher()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ClassSummary update(@PathVariable Long id, @Valid @RequestBody ClassRequest req) {
        return service.update(id, req, currentUser.requireTeacher());
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('TEACHER')")
    public ClassSummary archive(@PathVariable Long id, @Valid @RequestBody ArchiveRequest req) {
        return service.setArchived(id, req.archived(), currentUser.requireTeacher());
    }

    @PostMapping("/{id}/regenerate-code")
    @PreAuthorize("hasRole('TEACHER')")
    public ClassSummary regenerateCode(@PathVariable Long id) {
        return service.regenerateCode(id, currentUser.requireTeacher());
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasRole('TEACHER')")
    public List<RosterEntry> roster(@PathVariable Long id) {
        return service.roster(id, currentUser.requireTeacher());
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        service.removeStudent(id, studentId, currentUser.requireTeacher());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ClassSummary join(@Valid @RequestBody JoinRequest req) {
        return service.join(req.code(), currentUser.requireStudent());
    }
}
