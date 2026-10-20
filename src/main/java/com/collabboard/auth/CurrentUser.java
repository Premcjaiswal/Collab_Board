package com.collabboard.auth;

import com.collabboard.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Please sign in to continue.");
        }
        return user;
    }

    public AuthUser requireTeacher() {
        AuthUser user = get();
        if (!user.isTeacher()) {
            throw ApiException.forbidden("Only teachers can perform this action.");
        }
        return user;
    }

    public AuthUser requireStudent() {
        AuthUser user = get();
        if (!user.isStudent()) {
            throw ApiException.forbidden("Only students can perform this action.");
        }
        return user;
    }
}
