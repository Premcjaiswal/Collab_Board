package com.collabboard.auth;

import com.collabboard.auth.AuthDtos.LoginRequest;
import com.collabboard.auth.AuthDtos.RegisterResponse;
import com.collabboard.auth.AuthDtos.ResetPasswordRequest;
import com.collabboard.auth.AuthDtos.StudentRegisterRequest;
import com.collabboard.auth.AuthDtos.TeacherRegisterRequest;
import com.collabboard.auth.AuthDtos.VerifyRequest;
import com.collabboard.common.ApiException;
import com.collabboard.common.Texts;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.user.Role;
import com.collabboard.user.StudentProfile;
import com.collabboard.user.StudentProfileRepository;
import com.collabboard.user.TeacherProfile;
import com.collabboard.user.TeacherProfileRepository;
import com.collabboard.user.User;
import com.collabboard.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final TeacherProfileRepository teacherProfiles;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService codes;
    private final LoginAttemptService loginAttempts;
    private final RegistrationPolicy policy;
    private final NotificationService notifications;
    /** Used to keep login timing similar whether or not the account exists. */
    private final String dummyHash;

    public AuthService(UserRepository users, StudentProfileRepository studentProfiles,
                       TeacherProfileRepository teacherProfiles, PasswordEncoder passwordEncoder,
                       VerificationCodeService codes, LoginAttemptService loginAttempts,
                       RegistrationPolicy policy, NotificationService notifications) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.teacherProfiles = teacherProfiles;
        this.passwordEncoder = passwordEncoder;
        this.codes = codes;
        this.loginAttempts = loginAttempts;
        this.policy = policy;
        this.notifications = notifications;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-placeholder-1");
    }

    @Transactional
    public RegisterResponse registerStudent(StudentRegisterRequest req) {
        String email = Texts.normalizeEmail(req.email());
        ensureRegistrable(Role.STUDENT, email);
        User user = createUser(email, req.password(), req.fullName(), Role.STUDENT);
        StudentProfile profile = new StudentProfile(user, req.rollNumber().trim().toUpperCase());
        profile.setBranch(Texts.trimToNull(req.branch()));
        profile.setYearOfStudy(req.yearOfStudy());
        profile.setSemester(req.semester());
        studentProfiles.save(profile);
        return afterRegistration(user);
    }

    @Transactional
    public RegisterResponse registerTeacher(TeacherRegisterRequest req) {
        String email = Texts.normalizeEmail(req.email());
        ensureRegistrable(Role.TEACHER, email);
        User user = createUser(email, req.password(), req.fullName(), Role.TEACHER);
        TeacherProfile profile = new TeacherProfile(user);
        profile.setCollegeName(Texts.trimToNull(req.collegeName()));
        profile.setDesignation(Texts.trimToNull(req.designation()));
        teacherProfiles.save(profile);
        return afterRegistration(user);
    }

    private void ensureRegistrable(Role role, String email) {
        if (!policy.isAllowed(role, email)) {
            throw ApiException.badRequest("EMAIL_DOMAIN_NOT_ALLOWED",
                    "Please register with your college email address (" + String.join(", ", policy.domainsFor(role)) + ").");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_EXISTS", "An account with this email already exists. Sign in or reset your password.");
        }
    }

    private User createUser(String email, String password, String fullName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName.trim());
        user.setRole(role);
        user.setEmailVerified(false);
        return users.save(user);
    }

    private RegisterResponse afterRegistration(User user) {
        boolean sent = codes.issue(user, CodePurpose.EMAIL_VERIFICATION);
        String message = sent
                ? "Account created. We sent a 6-digit verification code to your email."
                : "Account created, but the verification email could not be sent right now. Use \"Resend code\" to try again.";
        return new RegisterResponse(message, user.getEmail(), sent);
    }

    /** Attempt counters must survive a failed check, hence no rollback for ApiException. */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyEmail(VerifyRequest req) {
        User user = users.findByEmail(Texts.normalizeEmail(req.email()))
                .filter(u -> !u.isEmailVerified())
                .orElseThrow(() -> ApiException.badRequest("INVALID_CODE", "The code is invalid or has expired."));
        codes.verify(user, CodePurpose.EMAIL_VERIFICATION, req.code());
        user.setEmailVerified(true);
        notifications.notify(user, NotificationType.ACCOUNT, "Email verified",
                "Your college email has been verified. Welcome to CollabBoard!", "#/dashboard");
    }

    /** @return true when a code was emailed; the controller always replies generically. */
    @Transactional
    public boolean resendVerification(String rawEmail) {
        Optional<User> user = users.findByEmail(Texts.normalizeEmail(rawEmail)).filter(u -> !u.isEmailVerified());
        return user.map(u -> codes.issue(u, CodePurpose.EMAIL_VERIFICATION)).orElse(false);
    }

    @Transactional(readOnly = true)
    public AuthUser authenticate(LoginRequest req, String clientIp) {
        Optional<User> found = users.findByEmail(Texts.normalizeEmail(req.email()));
        // The lockout is keyed on the stored address: the database collation also matches other spellings
        // (e.g. accented letters), which must not get their own attempt counters.
        String email = found.map(User::getEmail).orElse(Texts.normalizeEmail(req.email()));
        loginAttempts.checkAllowed(email, clientIp);
        boolean passwordOk;
        if (found.isPresent()) {
            passwordOk = passwordEncoder.matches(req.password(), found.get().getPasswordHash());
        } else {
            passwordEncoder.matches(req.password(), dummyHash);
            passwordOk = false;
        }
        if (!passwordOk || !found.get().isActive() || found.get().getRole() != req.role()) {
            loginAttempts.recordFailure(email, clientIp);
            throw ApiException.unauthorized("INVALID_CREDENTIALS",
                    "Invalid email or password for a " + req.role().name().toLowerCase() + " account.");
        }
        User user = found.get();
        if (!user.isEmailVerified()) {
            // Only revealed after the correct password, so it does not leak account state to guessers.
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Please verify your email address before signing in.");
        }
        loginAttempts.recordSuccess(email);
        return new AuthUser(user.getId(), user.getEmail(), user.getRole());
    }

    /** Always succeeds from the caller's perspective to avoid account enumeration. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        users.findByEmail(Texts.normalizeEmail(rawEmail))
                .filter(u -> u.isEmailVerified() && u.isActive())
                .ifPresent(u -> {
                    try {
                        codes.issue(u, CodePurpose.PASSWORD_RESET);
                    } catch (ApiException rateLimited) {
                        // Swallowed deliberately: the response must not reveal whether the account exists.
                    }
                });
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(ResetPasswordRequest req) {
        String email = Texts.normalizeEmail(req.email());
        User user = users.findByEmail(email)
                .filter(User::isEmailVerified)
                .orElseThrow(() -> ApiException.badRequest("INVALID_CODE", "The code is invalid or has expired."));
        codes.verify(user, CodePurpose.PASSWORD_RESET, req.code());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        loginAttempts.recordSuccess(user.getEmail());
        notifications.notify(user, NotificationType.ACCOUNT, "Password reset",
                "Your password was reset using an email code.", "#/settings");
    }
}
