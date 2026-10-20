package com.collabboard;

import com.collabboard.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression tests for defects found in the end-to-end QA pass. */
class QaRegressionTest extends IntegrationTest {

    private record Cls(Account teacher, long classId, Account a, Account b) {
    }

    private Cls cls(String p) throws Exception {
        Account t = teacher(p);
        long c = createClass(t, "QA " + p);
        String code = joinCode(t, c);
        Account a = student(p + "a");
        Account b = student(p + "b");
        join(a, code);
        join(b, code);
        return new Cls(t, c, a, b);
    }

    private Map<String, Object> project(long classId, String formation, int maxTeam) {
        Map<String, Object> m = new HashMap<>(Map.of("classId", classId, "title", "Group work", "description", "Build it",
                "deadline", inDays(5), "maxMarks", 20, "participationMode", "GROUP", "allowedExtensions", "pdf",
                "maxFileSizeMb", 5, "allowResubmission", true, "allowLate", false));
        if (formation != null) {
            m.put("teamFormation", formation);
            m.put("maxTeamSize", maxTeam);
        }
        return m;
    }

    private long submit(Account s, long assignmentId) throws Exception {
        return ok(upload("/api/assignments/" + assignmentId + "/submissions", s.session(), null, pdf("files", "w.pdf")))
                .get("submissionId").asLong();
    }

    @Test
    void lastMemberLeavingRemovesTheUnusedTeam() throws Exception {
        Cls x = cls("leave");
        long p = ok(postJson("/api/projects", project(x.classId(), "STUDENT_INVITE", 3), x.teacher().session())).get("id").asLong();
        long team = ok(postJson("/api/projects/" + p + "/teams", Map.of("name", "Solo"), x.a().session())).get("id").asLong();
        ok(postJson("/api/teams/" + team + "/leave", Map.of(), x.a().session()));
        assertThat(ok(getJson("/api/projects/" + p + "/my-team", x.a().session())).get("team").isNull()).isTrue();
        // The name is free again and the student can start over.
        ok(postJson("/api/projects/" + p + "/teams", Map.of("name", "Solo"), x.a().session()));
    }

    @Test
    void draftForAnOlderVersionCannotBePublished() throws Exception {
        Cls x = cls("stale");
        long a = createAssignment(x.teacher(), x.classId(), true, false);
        long sub = submit(x.a(), a);
        long draft = ok(postJson("/api/evaluation/submissions/" + sub + "/evaluations",
                Map.of("decision", "EVALUATED", "marks", 4, "publish", false), x.teacher().session())).get("id").asLong();
        submit(x.a(), a); // version 2
        postJson("/api/evaluation/evaluations/" + draft + "/publish", Map.of(), x.teacher().session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVALUATION_OUTDATED"));
        assertThat(ok(getJson("/api/assignments/" + a + "/my-submission", x.a().session())).get("status").asText())
                .isEqualTo("SUBMITTED");
    }

    @Test
    void maxMarksAndRubricAreLockedOnceEvaluated() throws Exception {
        Cls x = cls("lock");
        long a = createAssignment(x.teacher(), x.classId(), true, false);
        long sub = submit(x.a(), a);
        ok(postJson("/api/evaluation/submissions/" + sub + "/evaluations",
                Map.of("decision", "EVALUATED", "marks", 8.5, "publish", true), x.teacher().session()));
        Map<String, Object> body = new HashMap<>(Map.of("classId", x.classId(), "title", "Lab 1", "description", "Do the lab",
                "category", "LAB_REPORT", "deadline", inDays(3), "maxMarks", 5, "allowedExtensions", "pdf",
                "maxFileSizeMb", 5, "allowResubmission", true, "allowLate", false));
        putJson("/api/assignments/" + a, body, x.teacher().session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MARKS_LOCKED"));
        body.put("maxMarks", 10);
        body.put("rubric", List.of(Map.of("title", "All", "maxMarks", 10)));
        putJson("/api/assignments/" + a, body, x.teacher().session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RUBRIC_LOCKED"));
        body.remove("rubric");
        body.put("title", "Lab 1 (renamed)");
        ok(putJson("/api/assignments/" + a, body, x.teacher().session()));
    }

    @Test
    void archivedClassIsReadOnlyForStudents() throws Exception {
        Cls x = cls("archive");
        long a = createAssignment(x.teacher(), x.classId(), true, false);
        long sub = submit(x.a(), a);
        long p = ok(postJson("/api/projects", project(x.classId(), "STUDENT_INVITE", 3), x.teacher().session())).get("id").asLong();
        long team = ok(postJson("/api/projects/" + p + "/teams", Map.of("name", "T"), x.a().session())).get("id").asLong();
        ok(postJson("/api/classes/" + x.classId() + "/archive", Map.of("archived", true), x.teacher().session()));

        upload("/api/assignments/" + a + "/submissions", x.b().session(), null, pdf("files", "late.pdf"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLASS_ARCHIVED"));
        upload("/api/teams/" + team + "/files", x.a().session(), Map.of("category", "REPORT"), pdf("file", "r.pdf"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLASS_ARCHIVED"));
        postJson("/api/teams/" + team + "/invitations", Map.of("email", x.b().email()), x.a().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLASS_ARCHIVED"));
        postJson("/api/projects/" + p + "/teams", Map.of("name", "Late"), x.b().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLASS_ARCHIVED"));
        // Existing work stays readable.
        JsonNode mine = ok(getJson("/api/assignments/" + a + "/my-submission", x.a().session()));
        assertThat(mine.get("submissionId").asLong()).isEqualTo(sub);
        ok(getJson("/api/teams/" + team + "/workspace", x.a().session()));
    }

    @Test
    void unsupportedContentTypeIsAClientError() throws Exception {
        perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("{}"), null)
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void passwordChangeAndResetSignOutOtherSessions() throws Exception {
        Account s = student("sessions");
        MockHttpSession other = new MockHttpSession();
        ok(postJson("/api/auth/login", Map.of("email", s.email(), "password", PASSWORD, "role", "STUDENT"), other));

        ok(postJson("/api/profile/password", Map.of("currentPassword", PASSWORD, "newPassword", "Changed1Pass"), s.session()));
        getJson("/api/auth/me", other).andExpect(status().isUnauthorized());
        ok(getJson("/api/auth/me", s.session())); // the session that made the change stays signed in

        ok(postJson("/api/auth/forgot-password", Map.of("email", s.email()), null));
        ok(postJson("/api/auth/reset-password", Map.of("email", s.email(), "code", mail.lastCodeFor(s.email()),
                "newPassword", "Reset1Pass"), null));
        getJson("/api/auth/me", s.session()).andExpect(status().isUnauthorized());
    }

    @Test
    void revokedSessionCannotBeUsedThroughAnEncodedApiPath() throws Exception {
        Account s = student("encoded");
        MockHttpSession stale = new MockHttpSession();
        ok(postJson("/api/auth/login", Map.of("email", s.email(), "password", PASSWORD, "role", "STUDENT"), stale));
        ok(postJson("/api/profile/password", Map.of("currentPassword", PASSWORD, "newPassword", "Changed1Pass"), s.session()));
        // "%61" decodes to "a": routing and authorization see /api/auth/me, so the session check must as well.
        perform(get(URI.create("/%61pi/auth/me")), stale).andExpect(status().isUnauthorized());
        getJson("/api/auth/me", stale).andExpect(status().isUnauthorized());
    }

    @Test
    void editingAProjectWithoutTeamSettingsKeepsThem() throws Exception {
        Cls x = cls("keep");
        long p = ok(postJson("/api/projects", project(x.classId(), "TEACHER_ASSIGNED", 2), x.teacher().session())).get("id").asLong();
        ok(postJson("/api/projects/" + p + "/teams", Map.of("name", "A", "studentIds", List.of(x.a().id(), x.b().id())),
                x.teacher().session()));
        JsonNode updated = ok(putJson("/api/projects/" + p, project(x.classId(), null, 0), x.teacher().session()));
        assertThat(updated.get("teamFormation").asText()).isEqualTo("TEACHER_ASSIGNED");
        assertThat(updated.get("maxTeamSize").asInt()).isEqualTo(2);
    }

    @Test
    void outsidersCannotProbeProjectsOrTeamNames() throws Exception {
        Cls x = cls("probe");
        long p = ok(postJson("/api/projects", project(x.classId(), "STUDENT_INVITE", 3), x.teacher().session())).get("id").asLong();
        ok(postJson("/api/projects/" + p + "/teams", Map.of("name", "Alpha"), x.a().session()));
        Account outsider = student("probe-out");
        Account otherTeacher = teacher("probe-t");
        postJson("/api/projects/" + p + "/teams", Map.of("name", "Alpha"), outsider.session()).andExpect(status().isNotFound());
        postJson("/api/projects/" + p + "/teams", Map.of("name", "Alpha"), otherTeacher.session()).andExpect(status().isNotFound());
        getJson("/api/projects/" + p + "/my-team", outsider.session()).andExpect(status().isNotFound());
        getJson("/api/projects/" + p + "/teams", otherTeacher.session()).andExpect(status().isNotFound());
    }

    @Test
    void removedStudentsDashboardNoLongerShowsTheClassWork() throws Exception {
        Cls x = cls("dash");
        long a = createAssignment(x.teacher(), x.classId(), true, false);
        long sub = submit(x.a(), a);
        ok(postJson("/api/evaluation/submissions/" + sub + "/evaluations",
                Map.of("decision", "EVALUATED", "marks", 9, "publish", true), x.teacher().session()));
        assertThat(ok(getJson("/api/dashboard", x.a().session())).get("results")).hasSize(1);
        ok(deleteReq("/api/classes/" + x.classId() + "/students/" + x.a().id(), x.teacher().session()));
        JsonNode dash = ok(getJson("/api/dashboard", x.a().session()));
        assertThat(dash.get("results")).isEmpty();
        assertThat(dash.get("recentSubmissions")).isEmpty();
        // The teacher keeps the submission.
        ok(getJson("/api/evaluation/submissions/" + sub, x.teacher().session()));
    }
}
