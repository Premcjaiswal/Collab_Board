package com.collabboard.auth;

import com.collabboard.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain at least one letter and one digit.";

    public record StudentRegisterRequest(
            @NotBlank(message = "Full name is required.") @Size(max = 120) String fullName,
            @NotBlank(message = "College email is required.") @Email(message = "Enter a valid email address.") @Size(max = 190) String email,
            @NotBlank(message = "Password is required.") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotBlank(message = "Roll number is required.") @Size(max = 40)
            @Pattern(regexp = "^[A-Za-z0-9/_.-]+$", message = "Roll number may contain letters, digits and / _ . - only.") String rollNumber,
            @Size(max = 100) String branch,
            @Min(value = 1, message = "Year must be between 1 and 6.") @Max(value = 6, message = "Year must be between 1 and 6.") Integer yearOfStudy,
            @Min(value = 1, message = "Semester must be between 1 and 12.") @Max(value = 12, message = "Semester must be between 1 and 12.") Integer semester) {
    }

    public record TeacherRegisterRequest(
            @NotBlank(message = "Full name is required.") @Size(max = 120) String fullName,
            @NotBlank(message = "College email is required.") @Email(message = "Enter a valid email address.") @Size(max = 190) String email,
            @NotBlank(message = "Password is required.") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @Size(max = 150) String collegeName,
            @Size(max = 100) String designation) {
    }

    public record VerifyRequest(
            @NotBlank @Email String email,
            @NotBlank(message = "Enter the 6-digit code.") @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code.") String code) {
    }

    public record EmailRequest(@NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.") String email) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.") String email,
            @NotBlank(message = "Password is required.") @Size(max = 200) String password,
            @NotNull(message = "Choose student or teacher sign in.") Role role) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank(message = "Enter the 6-digit code.") @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code.") String code,
            @NotBlank(message = "New password is required.") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record RegisterResponse(String message, String email, boolean emailSent) {
    }

    public record MessageResponse(String message) {
    }

    public record CsrfResponse(String headerName, String token) {
    }
}
