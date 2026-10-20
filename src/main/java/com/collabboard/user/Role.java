package com.collabboard.user;

public enum Role {
    STUDENT,
    TEACHER;

    public String authority() {
        return "ROLE_" + name();
    }
}
