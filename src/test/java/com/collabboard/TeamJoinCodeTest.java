package com.collabboard;

import com.collabboard.support.IntegrationTest;
import com.collabboard.team.TeamJoinCodeBackfill;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit project/team-formation choice and joining student-created teams with a team code. */
class TeamJoinCodeTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TeamJoinCodeBackfill backfill;

    private record Cls(Account teacher, long classId, Account a, Account b, Account c, Account d) {
    }

    private Cls cls(String p) throws Exception {
        Account t = teacher(p);
        long c = createClass(t, "Codes " + p);
        String code = joinCode(t, c);
        Account a = student(p + "a");
        Account b = student(p + "b");
        Account cc = student(p + "c");
        Account d = student(p + "d");
        for (Account s : List.of(a, b, cc, d)) {
            join(s, code);
        }
        return new Cls(t, c, a, b, cc, d);
    }

    private Map<String, Object> projectBody(long classId, String mode, String formation, Integer maxTeam) {
        Map<String, Object> m = new HashMap<>(Map.of("classId", classId, "title", "Project", "description", "Build it",
                "deadline", inDays(5), "maxMarks", 20, "allowedExtensions", "pdf", "maxFileSizeMb", 5,
                "allowResubmission", true, "allowLate", false));
        if (mode != null) {
            m.put("participationMode", mode);
        }
        if (formation != null) {
            m.put("teamFormation", formation);
        }
        if (maxTeam != null) {
            m.put("maxTeamSize", maxTeam);
        }
        return m;
    }

    private long project(Cls x, String formation, int maxTeam) throws Exception {
        return ok(postJson("/api/projects", projectBody(x.classId(), "GROUP", formation, maxTeam), x.teacher().session()))
                .get("id").asLong();
    }

    private JsonNode createTeam(Account s, long projectId, String name) throws Exception {
        return ok(postJson("/api/projects/" + projectId + "/teams", Map.of("name", name), s.session()));
    }

    private org.springframework.test.web.servlet.ResultActions joinTeam(Account s, long projectId, String code) throws Exception {
        return postJson("/api/projects/" + projectId + "/teams/join", Map.of("code", code), s.session());
    }

    @Test
    void teacherMustExplicitlyChooseProjectTypeTeamFormationAndTeamSize() throws Exception {
        Cls x = cls("choose");
        postJson("/api/projects", projectBody(x.classId(), null, null, null), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.participationMode").exists());
        postJson("/api/projects", projectBody(x.classId(), "GROUP", null, 3), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TEAM_FORMATION_REQUIRED"));
        postJson("/api/projects", projectBody(x.classId(), "GROUP", "STUDENT_INVITE", null), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TEAM_SIZE_REQUIRED"));
        postJson("/api/projects", projectBody(x.classId(), "GROUP", "STUDENT_INVITE", 1), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.maxTeamSize").exists());
        long individual = ok(postJson("/api/projects", projectBody(x.classId(), "INDIVIDUAL", null, null), x.teacher().session()))
                .get("id").asLong();
        // Switching an untouched individual project to group work also needs an explicit choice.
        putJson("/api/projects/" + individual, projectBody(x.classId(), "GROUP", null, 3), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TEAM_FORMATION_REQUIRED"));
        putJson("/api/projects/" + individual, projectBody(x.classId(), "GROUP", "STUDENT_INVITE", null), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TEAM_SIZE_REQUIRED"));
        JsonNode group = ok(postJson("/api/projects", projectBody(x.classId(), "GROUP", "TEACHER_ASSIGNED", 3), x.teacher().session()));
        assertThat(group.get("teamFormation").asText()).isEqualTo("TEACHER_ASSIGNED");
    }

    @Test
    void classmateJoinsStudentCreatedTeamWithItsCode() throws Exception {
        Cls x = cls("join");
        long p = project(x, "STUDENT_INVITE", 3);
        JsonNode team = createTeam(x.a(), p, "Alpha");
        String code = team.get("joinCode").asText();
        assertThat(code).hasSize(8);

        // Codes are accepted with lower case and spaces.
        JsonNode joined = ok(joinTeam(x.b(), p, " " + code.toLowerCase().substring(0, 4) + " " + code.toLowerCase().substring(4) + " "));
        assertThat(joined.get("members").findValuesAsText("email")).containsExactlyInAnyOrder(x.a().email(), x.b().email());

        // Equal permissions: the joined member can use the workspace and sees the same code.
        JsonNode ws = ok(getJson("/api/teams/" + team.get("id").asLong() + "/workspace", x.b().session()));
        assertThat(ws.get("canEdit").asBoolean()).isTrue();
        assertThat(ws.get("team").get("joinCode").asText()).isEqualTo(code);
        assertThat(ws.get("activity").findValuesAsText("action")).contains("MEMBER_JOINED");
        assertThat(ok(getJson("/api/notifications", x.a().session())).findValuesAsText("title")).anyMatch(t -> t.contains("joined Alpha"));

        // The teacher sees the code; a classmate outside the team does not get it anywhere.
        assertThat(ok(getJson("/api/projects/" + p + "/teams", x.teacher().session())).get(0).get("joinCode").asText()).isEqualTo(code);
        assertThat(ok(getJson("/api/projects/" + p + "/my-team", x.c().session())).get("team").isNull()).isTrue();
        getJson("/api/teams/" + team.get("id").asLong() + "/workspace", x.c().session()).andExpect(status().isNotFound());
    }

    @Test
    void onlyEligibleStudentsWithAValidCodeCanJoin() throws Exception {
        Cls x = cls("elig");
        long p = project(x, "STUDENT_INVITE", 3);
        long other = project(x, "STUDENT_INVITE", 3);
        String code = createTeam(x.a(), p, "Alpha").get("joinCode").asText();
        String otherProjectCode = createTeam(x.c(), other, "Elsewhere").get("joinCode").asText();

        Account outsider = student("elig-out");
        joinTeam(outsider, p, code).andExpect(status().isNotFound());
        joinTeam(x.teacher(), p, code).andExpect(status().isForbidden());
        joinTeam(x.b(), p, "ZZZZZZZZ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TEAM_CODE"));
        joinTeam(x.b(), p, otherProjectCode).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TEAM_CODE"));
        joinTeam(x.a(), p, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_IN_TEAM"));

        // Already in another team for the same project.
        String betaCode = createTeam(x.d(), p, "Beta").get("joinCode").asText();
        joinTeam(x.d(), p, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_IN_TEAM"));
        ok(joinTeam(x.b(), p, betaCode));
        joinTeam(x.b(), p, betaCode).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_IN_TEAM"));
    }

    @Test
    void maximumTeamSizeCountsPendingInvitationsButNotTheJoinersOwn() throws Exception {
        Cls x = cls("size");
        long p = project(x, "STUDENT_INVITE", 3);
        JsonNode team = createTeam(x.a(), p, "Alpha");
        long teamId = team.get("id").asLong();
        String code = team.get("joinCode").asText();
        long invB = ok(postJson("/api/teams/" + teamId + "/invitations", Map.of("email", x.b().email()), x.a().session())).get("id").asLong();

        ok(joinTeam(x.c(), p, code)); // a + c + pending b = 3 = max
        joinTeam(x.d(), p, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_FULL"));

        // The invited classmate may use the code instead of the invitation; their reserved slot is used.
        JsonNode full = ok(joinTeam(x.b(), p, code));
        assertThat(full.get("members")).hasSize(3);
        assertThat(full.get("pendingInvitations")).isEmpty();
        assertThat(ok(getJson("/api/invitations", x.b().session()))).isEmpty();
        postJson("/api/invitations/" + invB + "/accept", Map.of(), x.b().session()).andExpect(status().is4xxClientError());
    }

    @Test
    void teacherAssignedTeamsHaveNoCodeAndCannotBeJoined() throws Exception {
        Cls x = cls("assigned");
        long p = project(x, "TEACHER_ASSIGNED", 2);
        JsonNode team = ok(postJson("/api/projects/" + p + "/teams", Map.of("name", "Set", "studentIds", List.of(x.a().id())),
                x.teacher().session()));
        assertThat(team.get("joinCode").isNull()).isTrue();
        joinTeam(x.b(), p, "ABCDEFGH").andExpect(status().isForbidden());
        postJson("/api/teams/" + team.get("id").asLong() + "/join-code", Map.of(), x.a().session()).andExpect(status().isForbidden());
    }

    @Test
    void anyMemberCanReplaceTheCodeAndTheOldOneStopsWorking() throws Exception {
        Cls x = cls("regen");
        long p = project(x, "STUDENT_INVITE", 4);
        JsonNode team = createTeam(x.a(), p, "Alpha");
        long teamId = team.get("id").asLong();
        String oldCode = team.get("joinCode").asText();
        ok(joinTeam(x.b(), p, oldCode));

        postJson("/api/teams/" + teamId + "/join-code", Map.of(), x.c().session()).andExpect(status().isNotFound());
        postJson("/api/teams/" + teamId + "/join-code", Map.of(), x.teacher().session()).andExpect(status().isForbidden());
        String newCode = ok(postJson("/api/teams/" + teamId + "/join-code", Map.of(), x.b().session())).get("joinCode").asText();
        assertThat(newCode).isNotEqualTo(oldCode);

        joinTeam(x.c(), p, oldCode).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TEAM_CODE"));
        ok(joinTeam(x.c(), p, newCode));
    }

    @Test
    void joiningIsBlockedOnceTheTeamIsEvaluatedOrTheClassArchived() throws Exception {
        Cls x = cls("locked");
        long p = project(x, "STUDENT_INVITE", 4);
        JsonNode team = createTeam(x.a(), p, "Alpha");
        long teamId = team.get("id").asLong();
        String code = team.get("joinCode").asText();
        ok(upload("/api/teams/" + teamId + "/files", x.a().session(), Map.of("category", "REPORT"), pdf("file", "r.pdf")));
        long sub = ok(postJson("/api/teams/" + teamId + "/submit", Map.of(), x.a().session())).get("submissionId").asLong();
        ok(postJson("/api/evaluation/submissions/" + sub + "/evaluations", Map.of("decision", "EVALUATED", "marks", 15, "publish", true),
                x.teacher().session()));
        joinTeam(x.b(), p, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_LOCKED"));

        long p2 = project(x, "STUDENT_INVITE", 4);
        String code2 = createTeam(x.c(), p2, "Gamma").get("joinCode").asText();
        ok(postJson("/api/classes/" + x.classId() + "/archive", Map.of("archived", true), x.teacher().session()));
        joinTeam(x.d(), p2, code2).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLASS_ARCHIVED"));
    }

    @Test
    void existingStudentCreatedTeamsAreGivenACodeAtStartup() throws Exception {
        Cls x = cls("backfill");
        long p = project(x, "STUDENT_INVITE", 3);
        long teamId = createTeam(x.a(), p, "Old team").get("id").asLong();
        jdbc.update("UPDATE teams SET join_code = NULL WHERE id = ?", teamId); // as for a team created before V2

        backfill.assignMissingCodes();

        String code = jdbc.queryForObject("SELECT join_code FROM teams WHERE id = ?", String.class, teamId);
        assertThat(code).hasSize(8);
        JsonNode joined = ok(joinTeam(x.b(), p, code));
        assertThat(joined.get("members")).hasSize(2); // existing membership kept
    }
}
