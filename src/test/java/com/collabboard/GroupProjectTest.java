package com.collabboard;

import com.collabboard.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupProjectTest extends IntegrationTest {

    private record Setup(Account teacher, long classId, long projectId, Account a, Account b, Account c, Account d) {
    }

    private Setup setup(String p, String formation, int maxTeam) throws Exception {
        Account t = teacher(p);
        long classId = createClass(t, "Proj " + p);
        String code = joinCode(t, classId);
        Account a = student(p + "a");
        Account b = student(p + "b");
        Account c = student(p + "c");
        Account d = student(p + "d");
        for (Account s : List.of(a, b, c, d)) {
            join(s, code);
        }
        long projectId = ok(postJson("/api/projects", Map.ofEntries(
                Map.entry("classId", classId), Map.entry("title", "Mini project"), Map.entry("description", "Build it"),
                Map.entry("deadline", inDays(5)), Map.entry("maxMarks", 20), Map.entry("participationMode", "GROUP"),
                Map.entry("teamFormation", formation), Map.entry("maxTeamSize", maxTeam),
                Map.entry("allowedExtensions", "pdf,zip,pptx"), Map.entry("maxFileSizeMb", 5),
                Map.entry("allowResubmission", true), Map.entry("allowLate", false),
                Map.entry("rubric", List.of(Map.of("title", "Implementation", "maxMarks", 12), Map.of("title", "Report", "maxMarks", 8)))),
                t.session())).get("id").asLong();
        return new Setup(t, classId, projectId, a, b, c, d);
    }

    private long createTeam(Account s, long projectId, String name) throws Exception {
        return ok(postJson("/api/projects/" + projectId + "/teams", Map.of("name", name), s.session())).get("id").asLong();
    }

    private long invite(Account from, long teamId, Account to) throws Exception {
        return ok(postJson("/api/teams/" + teamId + "/invitations", Map.of("email", to.email()), from.session())).get("id").asLong();
    }

    @Test
    void invitationsReserveSlotsAndOnlyAcceptedMembersGetWorkspaceAccess() throws Exception {
        Setup x = setup("inv", "STUDENT_INVITE", 3);
        long team = createTeam(x.a(), x.projectId(), "Alpha");
        long invB = invite(x.a(), team, x.b());
        long invC = invite(x.a(), team, x.c());

        // a + 2 pending = 3 = max: the team is full for further invitations.
        postJson("/api/teams/" + team + "/invitations", Map.of("email", x.d().email()), x.a().session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_FULL"));

        // Pending invitations grant no workspace access.
        getJson("/api/teams/" + team + "/workspace", x.b().session()).andExpect(status().isNotFound());

        // Only the invitee can accept.
        postJson("/api/invitations/" + invB + "/accept", Map.of(), x.c().session()).andExpect(status().isNotFound());
        ok(postJson("/api/invitations/" + invB + "/accept", Map.of(), x.b().session()));
        getJson("/api/teams/" + team + "/workspace", x.b().session()).andExpect(status().isOk());

        // Declining frees the slot.
        ok(postJson("/api/invitations/" + invC + "/decline", Map.of(), x.c().session()));
        postJson("/api/invitations/" + invC + "/accept", Map.of(), x.c().session()).andExpect(status().isConflict());
        invite(x.b(), team, x.d()); // any member (not just the creator) can invite

        JsonNode inviterNotes = ok(getJson("/api/notifications", x.a().session()));
        assertThat(inviterNotes.findValuesAsText("type")).contains("INVITATION_RESPONSE");
    }

    @Test
    void studentCannotBelongToTwoTeamsInSameProject() throws Exception {
        Setup x = setup("two", "STUDENT_INVITE", 4);
        long t1 = createTeam(x.a(), x.projectId(), "One");
        long t2 = createTeam(x.b(), x.projectId(), "Two");
        long inv = invite(x.b(), t2, x.c());
        invite(x.a(), t1, x.c());
        postJson("/api/projects/" + x.projectId() + "/teams", Map.of("name", "Three"), x.a().session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_IN_TEAM"));
        postJson("/api/teams/" + t1 + "/invitations", Map.of("email", x.b().email()), x.a().session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITEE_IN_TEAM"));
        ok(postJson("/api/invitations/" + inv + "/accept", Map.of(), x.c().session()));
        // Accepting one invitation cancels the student's other pending invitations for the project.
        assertThat(ok(getJson("/api/invitations", x.c().session()))).isEmpty();
        JsonNode one = ok(getJson("/api/teams/" + t1 + "/workspace", x.a().session()));
        assertThat(one.get("team").get("pendingInvitations")).isEmpty();
    }

    @Test
    void cannotInviteStudentsOutsideTheClass() throws Exception {
        Setup x = setup("outside", "STUDENT_INVITE", 4);
        long team = createTeam(x.a(), x.projectId(), "T");
        Account outsider = student("outsider");
        postJson("/api/teams/" + team + "/invitations", Map.of("email", outsider.email()), x.a().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVITEE_NOT_ELIGIBLE"));
        postJson("/api/teams/" + team + "/invitations", Map.of("email", x.teacher().email()), x.a().session())
                .andExpect(status().isBadRequest());
    }

    @Test
    void allMembersHaveEqualWorkspacePermissionsAndSharedEvaluation() throws Exception {
        Setup x = setup("equal", "STUDENT_INVITE", 3);
        long team = createTeam(x.a(), x.projectId(), "Equals");
        ok(postJson("/api/invitations/" + invite(x.a(), team, x.b()) + "/accept", Map.of(), x.b().session()));

        // B (not the creator) uploads; A replaces B's file; B deletes it; A restores it.
        JsonNode up = ok(upload("/api/teams/" + team + "/files", x.b().session(), Map.of("category", "REPORT"), pdf("file", "report.pdf")));
        long fileB = up.get("id").asLong();
        JsonNode replaced = ok(upload("/api/teams/" + team + "/files", x.a().session(), Map.of("replaceId", String.valueOf(fileB)),
                pdf("file", "report-v2.pdf")));
        long fileA = replaced.get("id").asLong();
        assertThat(replaced.get("category").asText()).isEqualTo("REPORT");
        deleteReq("/api/teams/" + team + "/files/" + fileA, x.b().session()).andExpect(status().isNoContent());
        ok(postJson("/api/teams/" + team + "/files/" + fileA + "/restore", Map.of(), x.a().session()));
        ok(upload("/api/teams/" + team + "/files", x.a().session(), Map.of("category", "SOURCE_CODE"), zip("file", "src.zip")));
        putJson("/api/teams/" + team + "/details", Map.of("githubUrl", "https://github.com/acme/logic-sim", "notes", "Notes by B"),
                x.b().session()).andExpect(status().isNoContent());
        putJson("/api/teams/" + team + "/details", Map.of("githubUrl", "javascript:alert(1)"), x.a().session())
                .andExpect(status().isBadRequest());

        JsonNode ws = ok(getJson("/api/teams/" + team + "/workspace", x.a().session()));
        assertThat(ws.get("activeFiles")).hasSize(2);
        assertThat(ws.get("history")).hasSize(1); // the replaced original is preserved
        List<String> actions = ws.get("activity").findValuesAsText("action");
        assertThat(actions).contains("FILE_UPLOADED", "FILE_REPLACED", "FILE_DELETED", "FILE_RESTORED", "GITHUB_UPDATED", "NOTES_UPDATED");

        // Workspace files can be downloaded by members and the teacher, but not by other students.
        String url = ws.get("activeFiles").get(0).get("file").get("downloadUrl").asText();
        mvc.perform(get(url).session(x.b().session())).andExpect(status().isOk());
        mvc.perform(get(url).session(x.teacher().session())).andExpect(status().isOk());
        mvc.perform(get(url).session(x.c().session())).andExpect(status().isNotFound());
        getJson("/api/teams/" + team + "/workspace", x.c().session()).andExpect(status().isNotFound());

        // B submits the final project (any member can submit).
        JsonNode submitted = ok(postJson("/api/teams/" + team + "/submit", Map.of(), x.b().session()));
        assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(submitted.get("versions").get(0).get("files")).hasSize(2);
        assertThat(submitted.get("versions").get(0).get("githubUrl").asText()).isEqualTo("https://github.com/acme/logic-sim");

        // Teacher evaluates with the rubric and publishes.
        JsonNode rows = ok(getJson("/api/evaluation/submissions?kind=PROJECT&itemId=" + x.projectId(), x.teacher().session()));
        long submissionId = rows.get(0).get("submissionId").asLong();
        JsonNode detail = ok(getJson("/api/evaluation/submissions/" + submissionId, x.teacher().session()));
        long c1 = detail.get("rubric").get(0).get("id").asLong();
        long c2 = detail.get("rubric").get(1).get("id").asLong();
        ok(postJson("/api/evaluation/submissions/" + submissionId + "/evaluations", Map.of("decision", "EVALUATED",
                "scores", List.of(Map.of("criterionId", c1, "score", 10.5), Map.of("criterionId", c2, "score", 7)),
                "feedback", "Great teamwork", "publish", true), x.teacher().session()));

        // Both members see the same marks and feedback.
        for (Account member : List.of(x.a(), x.b())) {
            JsonNode mine = ok(getJson("/api/projects/" + x.projectId() + "/my-submission", member.session()));
            assertThat(mine.get("status").asText()).isEqualTo("EVALUATED");
            assertThat(mine.get("evaluations").get(0).get("totalMarks").decimalValue()).isEqualByComparingTo("17.5");
            assertThat(mine.get("evaluations").get(0).get("feedback").asText()).isEqualTo("Great teamwork");
            assertThat(ok(getJson("/api/notifications", member.session())).findValuesAsText("type")).contains("EVALUATION_PUBLISHED");
        }
        // After evaluation the workspace and membership are locked for students.
        upload("/api/teams/" + team + "/files", x.a().session(), Map.of("category", "OTHER"), pdf("file", "late.pdf"))
                .andExpect(status().isConflict());
        postJson("/api/teams/" + team + "/leave", Map.of(), x.b().session()).andExpect(status().isConflict());
    }

    @Test
    void leavingMemberLosesAccessAndHistoryIsPreserved() throws Exception {
        Setup x = setup("leave", "STUDENT_INVITE", 3);
        long team = createTeam(x.a(), x.projectId(), "Leavers");
        ok(postJson("/api/invitations/" + invite(x.a(), team, x.b()) + "/accept", Map.of(), x.b().session()));
        ok(upload("/api/teams/" + team + "/files", x.b().session(), Map.of("category", "REPORT"), pdf("file", "b.pdf")));
        postJson("/api/teams/" + team + "/leave", Map.of(), x.b().session()).andExpect(status().isNoContent());
        getJson("/api/teams/" + team + "/workspace", x.b().session()).andExpect(status().isNotFound());
        JsonNode ws = ok(getJson("/api/teams/" + team + "/workspace", x.a().session()));
        assertThat(ws.get("team").get("members")).hasSize(1);
        assertThat(ws.get("activeFiles")).hasSize(1); // B's upload remains
        assertThat(ws.get("activity").findValuesAsText("action")).contains("MEMBER_LEFT");
    }

    @Test
    void teacherAssignedTeamsAreManagedByTeacherOnly() throws Exception {
        Setup x = setup("assigned", "TEACHER_ASSIGNED", 2);
        postJson("/api/projects/" + x.projectId() + "/teams", Map.of("name", "Mine"), x.a().session())
                .andExpect(status().isForbidden());
        long team = ok(postJson("/api/projects/" + x.projectId() + "/teams",
                Map.of("name", "Assigned", "studentIds", List.of(x.a().id(), x.b().id())), x.teacher().session())).get("id").asLong();
        postJson("/api/teams/" + team + "/members", Map.of("studentId", x.c().id()), x.teacher().session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_FULL"));
        postJson("/api/teams/" + team + "/leave", Map.of(), x.a().session()).andExpect(status().isForbidden());
        JsonNode ws = ok(getJson("/api/teams/" + team + "/workspace", x.a().session()));
        assertThat(ws.get("team").get("members")).hasSize(2);
        ok(deleteReq("/api/teams/" + team + "/members/" + x.b().id(), x.teacher().session()));
        getJson("/api/teams/" + team + "/workspace", x.b().session()).andExpect(status().isNotFound());
        // Students cannot use teacher-only team management endpoints.
        postJson("/api/teams/" + team + "/members", Map.of("studentId", x.d().id()), x.a().session()).andExpect(status().isForbidden());
    }

    @Test
    void individualProjectSubmissionAndGroupSubmitEndpointsAreSeparated() throws Exception {
        Account t = teacher("indiv");
        long classId = createClass(t, "Indiv");
        Account s = student("indiv");
        join(s, joinCode(t, classId));
        long projectId = ok(postJson("/api/projects", Map.ofEntries(
                Map.entry("classId", classId), Map.entry("title", "Solo"), Map.entry("description", "d"),
                Map.entry("deadline", inDays(5)), Map.entry("maxMarks", 10), Map.entry("participationMode", "INDIVIDUAL"),
                Map.entry("allowedExtensions", "pdf,zip"), Map.entry("maxFileSizeMb", 5),
                Map.entry("allowResubmission", true), Map.entry("allowLate", false)), t.session())).get("id").asLong();
        JsonNode res = ok(upload("/api/projects/" + projectId + "/submissions", s.session(),
                Map.of("githubUrl", "https://github.com/me/solo"), pdf("files", "solo.pdf")));
        assertThat(res.get("status").asText()).isEqualTo("SUBMITTED");
        getJson("/api/projects/" + projectId + "/my-team", s.session()).andExpect(status().isBadRequest());
    }
}
