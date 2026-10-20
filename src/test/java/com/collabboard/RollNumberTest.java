package com.collabboard;

import com.collabboard.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Roll numbers repeat across years, divisions and colleges; identity is the student account, not the roll number. */
class RollNumberTest extends IntegrationTest {

    private Account register(String prefix, String roll) throws Exception {
        String email = uniqueEmail(prefix);
        ok(postJson("/api/auth/register/student", Map.of("fullName", "Student " + prefix, "email", email,
                "password", PASSWORD, "rollNumber", roll), null));
        return verifyAndLogin(email, "STUDENT");
    }

    @Test
    void studentsMayShareARollNumberAndRemainSeparateAccounts() throws Exception {
        Account first = register("roll1", "cs-101");
        Account second = register("roll2", "CS-101");
        assertThat(first.id()).isNotEqualTo(second.id());

        JsonNode me1 = ok(getJson("/api/auth/me", first.session()));
        JsonNode me2 = ok(getJson("/api/auth/me", second.session()));
        assertThat(me1.get("rollNumber").asText()).isEqualTo("CS-101");
        assertThat(me2.get("rollNumber").asText()).isEqualTo("CS-101");
        assertThat(me1.get("email").asText()).isNotEqualTo(me2.get("email").asText());

        // Both can be in the same class; the roster and grading keep them apart by account.
        Account teacher = teacher("roll-t");
        long classId = createClass(teacher, "Roll class");
        String code = joinCode(teacher, classId);
        join(first, code);
        join(second, code);
        JsonNode roster = ok(getJson("/api/classes/" + classId + "/students", teacher.session()));
        assertThat(roster.findValuesAsText("rollNumber")).containsExactly("CS-101", "CS-101");
        assertThat(roster.findValues("studentId")).extracting(JsonNode::asLong).containsExactlyInAnyOrder(first.id(), second.id());

        long a = createAssignment(teacher, classId, true, false);
        ok(upload("/api/assignments/" + a + "/submissions", first.session(), null, pdf("files", "mine.pdf")));
        assertThat(ok(getJson("/api/assignments/" + a + "/my-submission", second.session())).get("status").asText())
                .isEqualTo("NOT_SUBMITTED");
    }
}
