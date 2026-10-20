package com.collabboard.user;

import com.collabboard.auth.AuthDtos.MessageResponse;
import com.collabboard.auth.CurrentUser;
import com.collabboard.auth.SessionCredentials;
import com.collabboard.user.ProfileDtos.ChangePasswordRequest;
import com.collabboard.user.ProfileDtos.ProfileResponse;
import com.collabboard.user.ProfileDtos.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService service;
    private final CurrentUser currentUser;
    private final SessionCredentials sessionCredentials;

    public ProfileController(ProfileService service, CurrentUser currentUser, SessionCredentials sessionCredentials) {
        this.service = service;
        this.currentUser = currentUser;
        this.sessionCredentials = sessionCredentials;
    }

    @GetMapping
    public ProfileResponse get() {
        return service.get(currentUser.get().id());
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        return service.update(currentUser.get().id(), req);
    }

    @PostMapping("/password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest request) {
        Long userId = currentUser.get().id();
        service.changePassword(userId, req);
        // Other sessions of this account are now signed out; this one stays signed in.
        sessionCredentials.bind(request, userId);
        return new MessageResponse("Password updated.");
    }
}
