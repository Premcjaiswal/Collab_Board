package com.collabboard;

import com.collabboard.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassroomTest extends IntegrationTest {

    @Test
    void teacherCreatesClassWithUniqueJoinCodeAndStudentJoinsImmediately() throws Exception {
        Account t = teacher("cls");
        long classId = createClass(t, "DLCS");
        String code = joinCode(t, classId);
        assertThat(code).matches("[A-Z2-9]{8}");

        Account s = student("cls");
        JsonNode joined = ok(postJson("/api/classes/join", Map.of("code", code.toLowerCase()), s.session()));
        assertThat(joined.get("id").asLong()).isEqualTo(classId);
        assertThat(joined.get("joinCode").isNull()).isTrue(); // students never see the join code in responses

        JsonNode roster = ok(getJson("/api/classes/" + classId + "/students", t.session()));
        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).get("email").asText()).isEqualTo(s.email());
    }

    @Test
    void duplicateEnrollmentIsPrevented() throws Exception {
        Account t = teacher("dupjoin");
        long classId = createClass(t, "BI");
        Account s = student("dupjoin");
        join(s, joinCode(t, classId));
        postJson("/api/classes/join", Map.of("code", joinCode(t, classId)), s.session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
    }

    @Test
    void invalidCodeIsRejected() throws Exception {
        Account s = student("badcode");
        postJson("/api/classes/join", Map.of("code", "ZZZZZZZZ"), s.session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CLASS_CODE"));
    }

    @Test
    void studentCanJoinMultipleClassesAndSeesOnlyThose() throws Exception {
        Account t1 = teacher("multi1");
        Account t2 = teacher("multi2");
        long c1 = createClass(t1, "Java Programming");
        long c2 = createClass(t2, "Java Programming");
        long c3 = createClass(t2, "Computer Networks");
        Account s = student("multi");
        join(s, joinCode(t1, c1));
        join(s, joinCode(t2, c2));
        JsonNode mine = ok(getJson("/api/classes", s.session()));
        assertThat(mine).hasSize(2);
        getJson("/api/classes/" + c3, s.session()).andExpect(status().isNotFound());
    }

    @Test
    void teachersOnlySeeAndManageTheirOwnClasses() throws Exception {
        Account t1 = teacher("own1");
        Account t2 = teacher("own2");
        long c1 = createClass(t1, "DLCS");
        assertThat(ok(getJson("/api/classes", t2.session()))).isEmpty();
        getJson("/api/classes/" + c1, t2.session()).andExpect(status().isNotFound());
        getJson("/api/classes/" + c1 + "/students", t2.session()).andExpect(status().isNotFound());
        putJson("/api/classes/" + c1, Map.of("name", "Hijack", "subjectName", "X", "semester", "1"), t2.session())
                .andExpect(status().isNotFound());
        postJson("/api/classes/" + c1 + "/regenerate-code", Map.of(), t2.session()).andExpect(status().isNotFound());
    }

    @Test
    void studentsCannotCreateClassesOrViewRosters() throws Exception {
        Account t = teacher("role");
        long c = createClass(t, "OS");
        Account s = student("role");
        join(s, joinCode(t, c));
        postJson("/api/classes", Map.of("name", "X", "subjectName", "Y", "semester", "Z"), s.session())
                .andExpect(status().isForbidden());
        getJson("/api/classes/" + c + "/students", s.session()).andExpect(status().isForbidden());
    }

    @Test
    void archivedClassRejectsJoinAndRegeneratedCodeInvalidatesOldOne() throws Exception {
        Account t = teacher("arch");
        long c = createClass(t, "TOC");
        String oldCode = joinCode(t, c);
        ok(postJson("/api/classes/" + c + "/regenerate-code", Map.of(), t.session()));
        Account s = student("arch");
        postJson("/api/classes/join", Map.of("code", oldCode), s.session()).andExpect(status().isBadRequest());
        ok(postJson("/api/classes/" + c + "/archive", Map.of("archived", true), t.session()));
        postJson("/api/classes/join", Map.of("code", joinCode(t, c)), s.session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLASS_ARCHIVED"));
    }

    @Test
    void teacherRemovesStudentFromClass() throws Exception {
        Account t = teacher("rm");
        long c = createClass(t, "SE");
        Account s = student("rm");
        join(s, joinCode(t, c));
        deleteReq("/api/classes/" + c + "/students/" + s.id(), t.session()).andExpect(status().isNoContent());
        getJson("/api/classes/" + c, s.session()).andExpect(status().isNotFound());
    }

    @Test
    void enrollmentCreatesNotificationsForStudentAndTeacher() throws Exception {
        Account t = teacher("notif");
        long c = createClass(t, "AI");
        Account s = student("notif");
        join(s, joinCode(t, c));
        JsonNode studentNotes = ok(getJson("/api/notifications", s.session()));
        assertThat(studentNotes.findValuesAsText("type")).contains("CLASS_ENROLLMENT");
        JsonNode teacherNotes = ok(getJson("/api/notifications", t.session()));
        assertThat(teacherNotes.findValuesAsText("type")).contains("CLASS_ENROLLMENT");
        long id = teacherNotes.get(0).get("id").asLong();
        long unreadBefore = ok(getJson("/api/notifications/unread-count", t.session())).get("count").asLong();
        ok(postJson("/api/notifications/" + id + "/read", Map.of(), t.session()));
        long unreadAfter = ok(getJson("/api/notifications/unread-count", t.session())).get("count").asLong();
        assertThat(unreadAfter).isEqualTo(unreadBefore - 1);
        // A user cannot mark someone else's notification.
        postJson("/api/notifications/" + id + "/read", Map.of(), s.session()).andExpect(status().isNotFound());
    }
}
