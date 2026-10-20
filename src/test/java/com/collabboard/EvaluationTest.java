package com.collabboard;

import com.collabboard.assignment.Assignment;
import com.collabboard.assignment.AssignmentRepository;
import com.collabboard.notification.DeadlineReminderJob;
import com.collabboard.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvaluationTest extends IntegrationTest {

    @Autowired
    AssignmentRepository assignments;
    @Autowired
    DeadlineReminderJob reminders;

    private record Setup(Account teacher, Account student, long classId, long assignmentId, long submissionId) {
    }

    private Setup submitted(String p) throws Exception {
        Account t = teacher(p);
        long c = createClass(t, "Eval " + p);
        Account s = student(p);
        join(s, joinCode(t, c));
        long a = createAssignment(t, c, true, false);
        long sub = ok(upload("/api/assignments/" + a + "/submissions", s.session(), null, pdf("files", "w.pdf"))).get("submissionId").asLong();
        return new Setup(t, s, c, a, sub);
    }

    private String evalUrl(Setup x) {
        return "/api/evaluation/submissions/" + x.submissionId() + "/evaluations";
    }

    @Test
    void marksMustBeWithinRangeAndRejectionNeedsReason() throws Exception {
        Setup x = submitted("range");
        postJson(evalUrl(x), Map.of("decision", "EVALUATED", "marks", 11, "publish", true), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MARKS_OUT_OF_RANGE"));
        postJson(evalUrl(x), Map.of("decision", "EVALUATED", "marks", -1, "publish", true), x.teacher().session())
                .andExpect(status().isBadRequest());
        postJson(evalUrl(x), Map.of("decision", "EVALUATED", "publish", true), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MARKS_REQUIRED"));
        postJson(evalUrl(x), Map.of("decision", "REJECTED", "publish", true), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        postJson(evalUrl(x), Map.of("decision", "REVISION_REQUESTED", "publish", true), x.teacher().session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FEEDBACK_REQUIRED"));
    }

    @Test
    void draftEvaluationsAreHiddenUntilPublishedAndHistoryIsPreserved() throws Exception {
        Setup x = submitted("draft");
        JsonNode draft = ok(postJson(evalUrl(x), Map.of("decision", "EVALUATED", "marks", 7.5, "feedback", "Good", "publish", false),
                x.teacher().session()));
        JsonNode mine = ok(getJson("/api/assignments/" + x.assignmentId() + "/my-submission", x.student().session()));
        assertThat(mine.get("evaluations")).isEmpty();
        assertThat(mine.get("status").asText()).isEqualTo("SUBMITTED");

        // Saving again creates a new record; the old one cannot be published any more.
        JsonNode second = ok(postJson(evalUrl(x), Map.of("decision", "EVALUATED", "marks", 8, "feedback", "Better", "publish", false),
                x.teacher().session()));
        postJson("/api/evaluation/evaluations/" + draft.get("id").asLong() + "/publish", Map.of(), x.teacher().session())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVALUATION_OUTDATED"));
        ok(postJson("/api/evaluation/evaluations/" + second.get("id").asLong() + "/publish", Map.of(), x.teacher().session()));

        mine = ok(getJson("/api/assignments/" + x.assignmentId() + "/my-submission", x.student().session()));
        assertThat(mine.get("status").asText()).isEqualTo("EVALUATED");
        assertThat(mine.get("evaluations")).hasSize(1);
        assertThat(mine.get("evaluations").get(0).get("totalMarks").decimalValue()).isEqualByComparingTo("8");

        JsonNode detail = ok(getJson("/api/evaluation/submissions/" + x.submissionId(), x.teacher().session()));
        assertThat(detail.get("evaluations")).hasSize(2);

        // Evaluated work is closed for resubmission.
        upload("/api/assignments/" + x.assignmentId() + "/submissions", x.student().session(), null, pdf("files", "again.pdf"))
                .andExpect(status().isConflict());
    }

    @Test
    void revisionRequestReopensSubmissionEvenAfterDeadline() throws Exception {
        Setup x = submitted("revise");
        ok(postJson(evalUrl(x), Map.of("decision", "REVISION_REQUESTED", "feedback", "Add conclusions", "publish", true), x.teacher().session()));
        Assignment a = assignments.findById(x.assignmentId()).orElseThrow();
        a.setDeadline(Instant.now().minus(2, ChronoUnit.HOURS));
        assignments.save(a);
        assertThat(ok(getJson("/api/notifications", x.student().session())).findValuesAsText("type")).contains("REVISION_REQUESTED");
        JsonNode res = ok(upload("/api/assignments/" + x.assignmentId() + "/submissions", x.student().session(), null, pdf("files", "fixed.pdf")));
        assertThat(res.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(res.get("versions")).hasSize(2);
        assertThat(res.get("evaluations").get(0).get("decision").asText()).isEqualTo("REVISION_REQUESTED");
    }

    @Test
    void rejectionIsFinal() throws Exception {
        Setup x = submitted("reject");
        ok(postJson(evalUrl(x), Map.of("decision", "REJECTED", "reason", "Plagiarised content", "publish", true), x.teacher().session()));
        JsonNode mine = ok(getJson("/api/assignments/" + x.assignmentId() + "/my-submission", x.student().session()));
        assertThat(mine.get("status").asText()).isEqualTo("REJECTED");
        assertThat(mine.get("evaluations").get(0).get("reason").asText()).isEqualTo("Plagiarised content");
        assertThat(mine.get("canSubmit").asBoolean()).isFalse();
        upload("/api/assignments/" + x.assignmentId() + "/submissions", x.student().session(), null, pdf("files", "x.pdf"))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyTheClassTeacherCanEvaluate() throws Exception {
        Setup x = submitted("authz");
        Account other = teacher("authz-other");
        getJson("/api/evaluation/submissions/" + x.submissionId(), other.session()).andExpect(status().isNotFound());
        postJson(evalUrl(x), Map.of("decision", "EVALUATED", "marks", 10, "publish", true), other.session())
                .andExpect(status().isNotFound());
        postJson(evalUrl(x), Map.of("decision", "EVALUATED", "marks", 10, "publish", true), x.student().session())
                .andExpect(status().isForbidden());
        assertThat(ok(getJson("/api/evaluation/submissions", other.session()))).isEmpty();
    }

    @Test
    void rosterIncludesStudentsWhoHaveNotSubmitted() throws Exception {
        Setup x = submitted("roster");
        Account lazy = student("roster-lazy");
        join(lazy, joinCode(x.teacher(), x.classId()));
        JsonNode rows = ok(getJson("/api/evaluation/submissions?kind=ASSIGNMENT&itemId=" + x.assignmentId(), x.teacher().session()));
        assertThat(rows).hasSize(2);
        assertThat(rows.findValuesAsText("status")).containsExactlyInAnyOrder("SUBMITTED", "NOT_SUBMITTED");
        JsonNode pending = ok(getJson("/api/evaluation/submissions?status=PENDING&classId=" + x.classId(), x.teacher().session()));
        assertThat(pending).hasSize(1);
    }

    @Test
    void deadlineRemindersAreCreatedOnceForStudentsWhoHaveNotSubmitted() throws Exception {
        Account t = teacher("remind");
        long c = createClass(t, "Remind");
        Account s = student("remind");
        Account done = student("remind-done");
        join(s, joinCode(t, c));
        join(done, joinCode(t, c));
        long a = createAssignment(t, c, true, false);
        ok(upload("/api/assignments/" + a + "/submissions", done.session(), null, pdf("files", "w.pdf")));
        Assignment entity = assignments.findById(a).orElseThrow();
        entity.setDeadline(Instant.now().plus(5, ChronoUnit.HOURS));
        assignments.save(entity);

        reminders.sendReminders(Instant.now());
        reminders.sendReminders(Instant.now());
        long count = ok(getJson("/api/notifications", s.session())).findValuesAsText("type").stream()
                .filter("DEADLINE_REMINDER"::equals).count();
        assertThat(count).isEqualTo(1);
        assertThat(ok(getJson("/api/notifications", done.session())).findValuesAsText("type")).doesNotContain("DEADLINE_REMINDER");
    }

    @Test
    void dashboardsContainOnlyTheCallersData() throws Exception {
        Setup x = submitted("dash");
        JsonNode teacherDash = ok(getJson("/api/dashboard", x.teacher().session()));
        assertThat(teacherDash.get("pendingSubmissions").asInt()).isEqualTo(1);
        assertThat(teacherDash.get("classes")).hasSize(1);
        JsonNode studentDash = ok(getJson("/api/dashboard", x.student().session()));
        assertThat(studentDash.get("recentSubmissions")).hasSize(1);
        Account stranger = student("dash-stranger");
        JsonNode strangerDash = ok(getJson("/api/dashboard", stranger.session()));
        assertThat(strangerDash.get("classes")).isEmpty();
        assertThat(strangerDash.get("upcomingAssignments")).isEmpty();
    }
}
