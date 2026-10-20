package com.collabboard;

import com.collabboard.support.IntegrationTest;
import com.collabboard.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    private String registerStudent(String email) throws Exception {
        ok(postJson("/api/auth/register/student", Map.of("fullName", "Riya Shah", "email", email, "password", PASSWORD,
                "rollNumber", "CS-101", "semester", 7), null));
        return mail.lastCodeFor(email);
    }

    @Test
    void registrationRequiresEmailVerificationBeforeLogin() throws Exception {
        String email = uniqueEmail("verify");
        String code = registerStudent(email);

        postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD, "role", "STUDENT"), new MockHttpSession())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        postJson("/api/auth/verify", Map.of("email", email, "code", code), null).andExpect(status().isOk());

        MockHttpSession session = new MockHttpSession();
        postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD, "role", "STUDENT"), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.rollNumber").value("CS-101"));
        getJson("/api/auth/me", session).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void passwordsAreHashedNotStoredInPlaintext() throws Exception {
        String email = uniqueEmail("hash");
        registerStudent(email);
        String hash = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(hash).doesNotContain(PASSWORD).startsWith("{bcrypt}");
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = uniqueEmail("dup");
        registerStudent(email);
        postJson("/api/auth/register/teacher", Map.of("fullName", "X", "email", email.toUpperCase(), "password", PASSWORD), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        postJson("/api/auth/register/student", Map.of("fullName", "", "email", "not-an-email", "password", "short",
                "rollNumber", ""), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.rollNumber").exists());
    }

    @Test
    void verificationCodeIsSingleUseAndWrongCodesAreLimited() throws Exception {
        String email = uniqueEmail("otp");
        String code = registerStudent(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/verify", Map.of("email", email, "code", wrong), null)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }
        // Attempts are exhausted: even the correct code is now refused until a new code is requested.
        postJson("/api/auth/verify", Map.of("email", email, "code", code), null).andExpect(status().isTooManyRequests());
    }

    @Test
    void usedCodeCannotBeReused() throws Exception {
        String email = uniqueEmail("reuse");
        String code = registerStudent(email);
        postJson("/api/auth/verify", Map.of("email", email, "code", code), null).andExpect(status().isOk());
        postJson("/api/auth/verify", Map.of("email", email, "code", code), null).andExpect(status().isBadRequest());
    }

    @Test
    void resendHasCooldown() throws Exception {
        String email = uniqueEmail("cool");
        registerStudent(email);
        postJson("/api/auth/resend-verification", Map.of("email", email), null).andExpect(status().isTooManyRequests());
        // Unknown emails get the same generic success response (no account enumeration).
        postJson("/api/auth/resend-verification", Map.of("email", uniqueEmail("nobody")), null).andExpect(status().isOk());
    }

    @Test
    void loginRejectsWrongPasswordAndWrongPortal() throws Exception {
        Account s = student("portal");
        postJson("/api/auth/login", Map.of("email", s.email(), "password", "Wrong1234", "role", "STUDENT"), new MockHttpSession())
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        postJson("/api/auth/login", Map.of("email", s.email(), "password", PASSWORD, "role", "TEACHER"), new MockHttpSession())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedLoginFailuresLockTheAccountTemporarily() throws Exception {
        Account s = student("lock");
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/login", Map.of("email", s.email(), "password", "Wrong1234", "role", "STUDENT"), new MockHttpSession())
                    .andExpect(status().isUnauthorized());
        }
        postJson("/api/auth/login", Map.of("email", s.email(), "password", PASSWORD, "role", "STUDENT"), new MockHttpSession())
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void forgotAndResetPassword() throws Exception {
        Account s = student("reset");
        postJson("/api/auth/forgot-password", Map.of("email", s.email()), null).andExpect(status().isOk());
        String code = mail.lastCodeFor(s.email());
        postJson("/api/auth/reset-password", Map.of("email", s.email(), "code", code, "newPassword", "NewPass99"), null)
                .andExpect(status().isOk());
        postJson("/api/auth/login", Map.of("email", s.email(), "password", PASSWORD, "role", "STUDENT"), new MockHttpSession())
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("email", s.email(), "password", "NewPass99", "role", "STUDENT"), new MockHttpSession())
                .andExpect(status().isOk());
        // Unknown email: same generic response.
        postJson("/api/auth/forgot-password", Map.of("email", uniqueEmail("ghost")), null).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedApiAccessIsRejected() throws Exception {
        mvc.perform(get("/api/classes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        Account t = teacher("csrf");
        mvc.perform(post("/api/classes").session(t.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"subjectName\":\"Y\",\"semester\":\"Z\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        Account s = student("logout");
        postJson("/api/auth/logout", Map.of(), s.session()).andExpect(status().isOk());
        assertThat(s.session().isInvalid()).isTrue();
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        Account s = student("chpw");
        postJson("/api/profile/password", Map.of("currentPassword", "WrongOld1", "newPassword", "Another12"), s.session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        postJson("/api/profile/password", Map.of("currentPassword", PASSWORD, "newPassword", "Another12"), s.session())
                .andExpect(status().isOk());
    }

    @Test
    void profileUpdateCannotChangeProtectedFields() throws Exception {
        Account s = student("profile");
        JsonNode before = ok(getJson("/api/profile", s.session()));
        JsonNode after = ok(putJson("/api/profile", Map.of("fullName", "New Name", "branch", "IT", "semester", 8,
                "email", "hacker@evil.com", "rollNumber", "HACK", "role", "TEACHER"), s.session()));
        assertThat(after.get("fullName").asText()).isEqualTo("New Name");
        assertThat(after.get("email").asText()).isEqualTo(before.get("email").asText());
        assertThat(after.get("rollNumber").asText()).isEqualTo(before.get("rollNumber").asText());
        assertThat(after.get("role").asText()).isEqualTo("STUDENT");
    }

    @Test
    void errorResponsesDoNotLeakStackTraces() throws Exception {
        String body = perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"), null)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("at com.");
    }
}
