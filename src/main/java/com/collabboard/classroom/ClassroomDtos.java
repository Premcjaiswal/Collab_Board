package com.collabboard.classroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ClassroomDtos {

    private ClassroomDtos() {
    }

    public record ClassRequest(
            @NotBlank(message = "Class name is required.") @Size(max = 150) String name,
            @NotBlank(message = "Subject name is required.") @Size(max = 150) String subjectName,
            @NotBlank(message = "Semester/year is required.") @Size(max = 40) String semester,
            @Size(max = 40) String division,
            @Size(max = 1000) String description) {
    }

    public record JoinRequest(@NotBlank(message = "Enter a class code.") @Size(max = 20) String code) {
    }

    public record ArchiveRequest(@NotNull Boolean archived) {
    }

    /** joinCode is only populated for the class teacher. */
    public record ClassSummary(Long id, String name, String subjectName, String semester, String division,
                               String description, String displayName, String joinCode, String teacherName,
                               long studentCount, long assignmentCount, long projectCount, boolean archived,
                               Instant createdAt, Instant joinedAt) {
    }

    public record RosterEntry(Long studentId, String fullName, String email, String rollNumber, String branch,
                              Instant joinedAt) {
    }
}
