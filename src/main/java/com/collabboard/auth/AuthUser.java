package com.collabboard.auth;

import com.collabboard.user.Role;

import java.io.Serial;
import java.io.Serializable;

/** Minimal authenticated principal kept in the HTTP session. Identity is always taken from here, never from the client. */
public record AuthUser(Long id, String email, Role role) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public boolean isTeacher() {
        return role == Role.TEACHER;
    }

    public boolean isStudent() {
        return role == Role.STUDENT;
    }
}
