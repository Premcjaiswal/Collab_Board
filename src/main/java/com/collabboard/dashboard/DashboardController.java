package com.collabboard.dashboard;

import com.collabboard.auth.AuthUser;
import com.collabboard.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Returns the student or teacher dashboard depending on the caller's role. */
    @GetMapping
    public Object dashboard() {
        AuthUser user = currentUser.get();
        return user.isTeacher() ? service.teacher(user) : service.student(user);
    }
}
