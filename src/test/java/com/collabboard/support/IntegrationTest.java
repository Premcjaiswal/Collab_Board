package com.collabboard.support;

import com.collabboard.auth.LoginAttemptService;
import com.collabboard.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for full-stack integration tests: real Spring context, security filter chain, Flyway schema (H2 in
 * MySQL mode) and MockMvc. Each test uses unique emails, so tests do not interfere with each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    public static final String PASSWORD = "Passw0rd!";

    @TestConfiguration
    public static class TestBeans {
        @Bean
        @Primary
        RecordingEmailService recordingEmailService(ObjectProvider<JavaMailSender> sender, AppProperties props) {
            return new RecordingEmailService(sender, props);
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected RecordingEmailService mail;
    @Autowired
    protected LoginAttemptService loginAttempts;

    @BeforeEach
    void resetRateLimits() {
        loginAttempts.reset();
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@college.edu";
    }

    // ------------------------------------------------------------------ HTTP helpers

    /**
     * Sends the request the way the browser client does: the XSRF-TOKEN cookie plus the same value in the
     * X-XSRF-TOKEN header (double-submit cookie pattern).
     */
    protected ResultActions perform(MockHttpServletRequestBuilder req, MockHttpSession session) throws Exception {
        if (session != null) {
            req.session(session);
        }
        String token = UUID.randomUUID().toString();
        return mvc.perform(req.cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token));
    }

    protected ResultActions getJson(String url, MockHttpSession s) throws Exception {
        return perform(get(url), s);
    }

    protected ResultActions postJson(String url, Object body, MockHttpSession s) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)), s);
    }

    protected ResultActions putJson(String url, Object body, MockHttpSession s) throws Exception {
        return perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)), s);
    }

    protected ResultActions deleteReq(String url, MockHttpSession s) throws Exception {
        return perform(delete(url), s);
    }

    protected ResultActions upload(String url, MockHttpSession s, Map<String, String> params, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart(url);
        for (MockMultipartFile f : files) {
            b.file(f);
        }
        if (params != null) {
            params.forEach(b::param);
        }
        return perform(b, s);
    }

    protected JsonNode body(ResultActions r) throws Exception {
        MvcResult res = r.andReturn();
        String content = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isEmpty() ? json.createObjectNode() : json.readTree(content);
    }

    protected JsonNode ok(ResultActions r) throws Exception {
        return body(r.andExpect(status().is2xxSuccessful()));
    }

    // ------------------------------------------------------------------ fixtures

    protected static MockMultipartFile pdf(String part, String name) {
        return new MockMultipartFile(part, name, "application/pdf",
                ("%PDF-1.4\n% test document " + UUID.randomUUID() + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
    }

    protected static MockMultipartFile zip(String part, String name) {
        byte[] bytes = new byte[]{0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        return new MockMultipartFile(part, name, "application/zip", bytes);
    }

    public record Account(long id, String email, MockHttpSession session) {
    }

    protected Account student(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        ok(postJson("/api/auth/register/student", Map.of("fullName", "Student " + prefix, "email", email,
                "password", PASSWORD, "rollNumber", "R" + Math.abs(email.hashCode() % 100000), "branch", "CSE",
                "yearOfStudy", 4, "semester", 7), null));
        return verifyAndLogin(email, "STUDENT");
    }

    protected Account teacher(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        ok(postJson("/api/auth/register/teacher", Map.of("fullName", "Teacher " + prefix, "email", email,
                "password", PASSWORD, "collegeName", "Test College", "designation", "Professor"), null));
        return verifyAndLogin(email, "TEACHER");
    }

    protected Account verifyAndLogin(String email, String role) throws Exception {
        ok(postJson("/api/auth/verify", Map.of("email", email, "code", mail.lastCodeFor(email)), null));
        MockHttpSession session = new MockHttpSession();
        JsonNode me = ok(postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD, "role", role), session));
        return new Account(me.get("id").asLong(), email, session);
    }

    protected long createClass(Account teacher, String name) throws Exception {
        return ok(postJson("/api/classes", Map.of("name", name, "subjectName", name + " subject", "semester", "Semester 7",
                "division", "A"), teacher.session())).get("id").asLong();
    }

    protected String joinCode(Account teacher, long classId) throws Exception {
        return ok(getJson("/api/classes/" + classId, teacher.session())).get("joinCode").asText();
    }

    protected void join(Account student, String code) throws Exception {
        ok(postJson("/api/classes/join", Map.of("code", code), student.session()));
    }

    protected static String inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).toString();
    }

    protected long createAssignment(Account teacher, long classId, boolean resubmission, boolean late) throws Exception {
        return ok(postJson("/api/assignments", Map.ofEntries(
                Map.entry("classId", classId), Map.entry("title", "Lab 1"), Map.entry("description", "Do the lab"),
                Map.entry("category", "LAB_REPORT"), Map.entry("deadline", inDays(3)), Map.entry("maxMarks", 10),
                Map.entry("allowedExtensions", "pdf"), Map.entry("maxFileSizeMb", 5),
                Map.entry("allowResubmission", resubmission), Map.entry("allowLate", late)), teacher.session())).get("id").asLong();
    }
}
