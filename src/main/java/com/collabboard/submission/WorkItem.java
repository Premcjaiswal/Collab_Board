package com.collabboard.submission;

import com.collabboard.classroom.Classroom;

import java.time.Instant;

/** Common view of an assignment or a project for submission/evaluation rules. */
public interface WorkItem {

    Long getId();

    Classroom getClassroom();

    String getTitle();

    Instant getDeadline();

    int getMaxMarks();

    String getAllowedExtensions();

    int getMaxFileSizeMb();

    boolean isAllowResubmission();

    boolean isAllowLate();
}
