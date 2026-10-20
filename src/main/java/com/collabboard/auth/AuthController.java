package com.collabboard.auth;

import com.collabboard.auth.AuthDtos.EmailRequest;
import com.collabboard.auth.AuthDtos.LoginRequest;
import com.collabboard.auth.AuthDtos.MessageResponse;
import com.collabboard.auth.AuthDtos.RegisterResponse;
import com.collabboard.auth.AuthDtos.ResetPasswordRequest;
import com.collabboard.auth.AuthDtos.StudentRegisterRequest;
import com.collabboard.auth.AuthDtos.TeacherRegisterRequest;
import com.collabboard.auth.AuthDtos.VerifyRequest;
import com.collabboard.user.ProfileDtos.ProfileResponse;
import com.collabboard.user.ProfileService;
import com.collabboard.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String GENERIC_CODE_SENT =
            "If an unverified account exists for this email, a new code has been sent.";

    private final AuthService authService;
    private final ProfileService profileService;
    private final CurrentUser currentUser;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final RegistrationPolicy policy;
    private final SessionCredentials sessionCredentials;

    public AuthController(AuthService authService, ProfileService profileService, CurrentUser currentUser,
                          SecurityContextRepository securityContextRepository,
                          CookieCsrfTokenRepository csrfTokenRepository, RegistrationPolicy policy,
                          SessionCredentials sessionCredentials) {
        this.authService = authService;
        this.profileService = profileService;
        this.currentUser = currentUser;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.policy = policy;
        this.sessionCredentials = sessionCredentials;
    }

    /** Ensures the XSRF-TOKEN cookie is issued; the client reads the cookie, not this body. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    /** Public, non-sensitive registration hints for the UI. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("studentEmailDomains", policy.domainsFor(Role.STUDENT),
                "teacherEmailDomains", policy.domainsFor(Role.TEACHER));
    }

    @PostMapping("/register/student")
    public ResponseEntity<RegisterResponse> registerStudent(@Valid @RequestBody StudentRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerStudent(req));
    }

    @PostMapping("/register/teacher")
    public ResponseEntity<RegisterResponse> registerTeacher(@Valid @RequestBody TeacherRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerTeacher(req));
    }

    @PostMapping("/verify")
    public MessageResponse verify(@Valid @RequestBody VerifyRequest req) {
        authService.verifyEmail(req);
        return new MessageResponse("Email verified. You can now sign in.");
    }

    @PostMapping("/resend-verification")
    public MessageResponse resend(@Valid @RequestBody EmailRequest req) {
        authService.resendVerification(req.email());
        return new MessageResponse(GENERIC_CODE_SENT);
    }

    @PostMapping("/login")
    public ProfileResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                                 HttpServletResponse response) {
        AuthUser user = authService.authenticate(req, request.getRemoteAddr());

        // Prevent session fixation: rotate the id of any pre-existing session.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority(user.role().authority()))));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        sessionCredentials.bind(request, user.id());
        // Rotate the CSRF token on authentication; a fresh one is issued on the next request.
        csrfTokenRepository.saveToken(null, request, response);
        return profileService.get(user.id());
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest req) {
        authService.forgotPassword(req.email());
        return new MessageResponse("If a verified account exists for this email, a reset code has been sent.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return new MessageResponse("Password reset. You can now sign in with your new password.");
    }

    @GetMapping("/me")
    public ProfileResponse me() {
        return profileService.get(currentUser.get().id());
    }
}
