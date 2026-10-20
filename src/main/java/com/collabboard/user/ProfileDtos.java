package com.collabboard.user;

import com.collabboard.auth.AuthDtos;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileResponse(Long id, String email, Role role, String fullName, boolean emailVerified,
                                  Instant createdAt, String rollNumber, String branch, Integer yearOfStudy,
                                  Integer semester, String collegeName, String designation) {
    }

    /**
     * Only these fields are editable. Email, role and roll number are protected identity fields
     * because enrollment, team membership and grades are tied to them.
     */
    public record UpdateProfileRequest(
            @NotBlank(message = "Full name is required.") @Size(max = 120) String fullName,
            @Size(max = 100) String branch,
            @Min(value = 1, message = "Year must be between 1 and 6.") @Max(value = 6, message = "Year must be between 1 and 6.") Integer yearOfStudy,
            @Min(value = 1, message = "Semester must be between 1 and 12.") @Max(value = 12, message = "Semester must be between 1 and 12.") Integer semester,
            @Size(max = 150) String collegeName,
            @Size(max = 100) String designation) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required.") @Size(max = 200) String currentPassword,
            @NotBlank(message = "New password is required.")
            @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String newPassword) {
    }

    /** Small public view of a user used inside other DTOs (rosters, team members, submitters). */
    public record UserSummary(Long id, String fullName, String email, String rollNumber) {
    }
}
