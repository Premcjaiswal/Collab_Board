package com.collabboard;

import com.collabboard.assignment.Assignment;
import com.collabboard.assignment.AssignmentRepository;
import com.collabboard.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentSubmissionTest extends IntegrationTest {

    @Autowired
    AssignmentRepository assignments;

    private record Setup(Account teacher, Account student, long classId, long assignmentId) {
    }

    private Setup setup(String p, boolean resubmission, boolean late) throws Exception {
        Account t = teacher(p);
        long c = createClass(t, "Class " + p);
        Account s = student(p);
        join(s, joinCode(t, c));
        return new Setup(t, s, c, createAssignment(t, c, resubmission, late));
    }

    private void moveDeadlineToPast(long assignmentId) {
        Assignment a = assignments.findById(assignmentId).orElseThrow();
        a.setDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        assignments.save(a);
    }

    private String submitUrl(long id) {
        return "/api/assignments/" + id + "/submissions";
    }

    @Test
    void studentSubmitsAndResubmitsCreatingVersionHistory() throws Exception {
        Setup x = setup("sub", true, false);
        JsonNode v1 = ok(upload(submitUrl(x.assignmentId()), x.student().session(), Map.of("notes", "first"), pdf("files", "report.pdf")));
        assertThat(v1.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(v1.get("versions")).hasSize(1);

        JsonNode v2 = ok(upload(submitUrl(x.assignmentId()), x.student().session(), null, pdf("files", "report-v2.pdf")));
        assertThat(v2.get("versions")).hasSize(2);
        assertThat(v2.get("versions").get(0).get("versionNo").asInt()).isEqualTo(2);
        assertThat(v2.get("versions").get(1).get("files").get(0).get("name").asText()).isEqualTo("report.pdf");

        // Confirmation notification for the student.
        assertThat(ok(getJson("/api/notifications", x.student().session())).findValuesAsText("type")).contains("SUBMISSION");
        // Teacher stats reflect the submission.
        JsonNode view = ok(getJson("/api/assignments/" + x.assignmentId(), x.teacher().session()));
        assertThat(view.get("stats").get("submitted").asInt()).isEqualTo(1);
    }

    @Test
    void disallowedFileTypeAndSpoofedContentAreRejected() throws Exception {
        Setup x = setup("type", true, false);
        MockMultipartFile exe = new MockMultipartFile("files", "virus.exe", "application/octet-stream", new byte[]{0x4D, 0x5A, 1, 2});
        upload(submitUrl(x.assignmentId()), x.student().session(), null, exe)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
        MockMultipartFile fakePdf = new MockMultipartFile("files", "fake.pdf", "application/pdf", "MZ not a pdf".getBytes(StandardCharsets.US_ASCII));
        upload(submitUrl(x.assignmentId()), x.student().session(), null, fakePdf)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));
        upload(submitUrl(x.assignmentId()), x.student().session(), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    }

    @Test
    void fileSizeLimitIsEnforced() throws Exception {
        Setup x = setup("size", true, false);
        byte[] big = new byte[6 * 1024 * 1024];
        System.arraycopy("%PDF".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 4);
        upload(submitUrl(x.assignmentId()), x.student().session(), null, new MockMultipartFile("files", "big.pdf", "application/pdf", big))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void downloadsAreAuthorizedPerSubmission() throws Exception {
        Setup x = setup("dl", true, false);
        JsonNode sub = ok(upload(submitUrl(x.assignmentId()), x.student().session(), null, pdf("files", "mine.pdf")));
        String url = sub.get("versions").get(0).get("files").get(0).get("downloadUrl").asText();

        mvc.perform(get(url).session(x.student().session())).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(get(url).session(x.teacher().session())).andExpect(status().isOk());

        Account classmate = student("dl-other");
        join(classmate, joinCode(x.teacher(), x.classId()));
        mvc.perform(get(url).session(classmate.session())).andExpect(status().isNotFound());
        Account otherTeacher = teacher("dl-other");
        mvc.perform(get(url).session(otherTeacher.session())).andExpect(status().isNotFound());
    }

    @Test
    void studentsCannotSeeOtherStudentsSubmissionsOrUnenrolledWork() throws Exception {
        Setup x = setup("priv", true, false);
        ok(upload(submitUrl(x.assignmentId()), x.student().session(), null, pdf("files", "a.pdf")));
        Account classmate = student("priv2");
        join(classmate, joinCode(x.teacher(), x.classId()));
        JsonNode mine = ok(getJson("/api/assignments/" + x.assignmentId() + "/my-submission", classmate.session()));
        assertThat(mine.get("status").asText()).isEqualTo("NOT_SUBMITTED");
        assertThat(mine.get("versions")).isEmpty();
        getJson("/api/evaluation/submissions", classmate.session()).andExpect(status().isForbidden());

        Account outsider = student("priv3");
        getJson("/api/assignments/" + x.assignmentId(), outsider.session()).andExpect(status().isNotFound());
        upload(submitUrl(x.assignmentId()), outsider.session(), null, pdf("files", "x.pdf")).andExpect(status().isNotFound());
    }

    @Test
    void teachersCannotSubmitAndOtherTeachersCannotEditAssignments() throws Exception {
        Setup x = setup("tsub", true, false);
        upload(submitUrl(x.assignmentId()), x.teacher().session(), null, pdf("files", "a.pdf")).andExpect(status().isForbidden());
        Account other = teacher("tsub2");
        deleteReq("/api/assignments/" + x.assignmentId(), other.session()).andExpect(status().isNotFound());
    }

    @Test
    void deadlineBlocksSubmissionUnlessLateAllowed() throws Exception {
        Setup strict = setup("strict", true, false);
        moveDeadlineToPast(strict.assignmentId());
        upload(submitUrl(strict.assignmentId()), strict.student().session(), null, pdf("files", "a.pdf"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_CLOSED"));

        Setup lenient = setup("lenient", true, true);
        moveDeadlineToPast(lenient.assignmentId());
        JsonNode res = ok(upload(submitUrl(lenient.assignmentId()), lenient.student().session(), null, pdf("files", "a.pdf")));
        assertThat(res.get("status").asText()).isEqualTo("LATE");
        assertThat(res.get("versions").get(0).get("late").asBoolean()).isTrue();
        // Resubmission after the deadline stays closed even when late first submissions are accepted.
        upload(submitUrl(lenient.assignmentId()), lenient.student().session(), null, pdf("files", "b.pdf"))
                .andExpect(status().isConflict());
    }

    @Test
    void resubmissionCanBeDisabled() throws Exception {
        Setup x = setup("once", false, false);
        ok(upload(submitUrl(x.assignmentId()), x.student().session(), null, pdf("files", "a.pdf")));
        upload(submitUrl(x.assignmentId()), x.student().session(), null, pdf("files", "b.pdf"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_CLOSED"));
    }

    @Test
    void assignmentCreationIsValidated() throws Exception {
        Account t = teacher("val");
        long c = createClass(t, "Val");
        postJson("/api/assignments", Map.ofEntries(Map.entry("classId", c), Map.entry("title", "Bad"), Map.entry("description", "d"),
                Map.entry("category", "ASSIGNMENT"), Map.entry("deadline", inDays(-1)), Map.entry("maxMarks", 10),
                Map.entry("allowedExtensions", "pdf"), Map.entry("maxFileSizeMb", 5)), t.session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DEADLINE_IN_PAST"));
        postJson("/api/assignments", Map.ofEntries(Map.entry("classId", c), Map.entry("title", "Bad"), Map.entry("description", "d"),
                Map.entry("category", "ASSIGNMENT"), Map.entry("deadline", inDays(1)), Map.entry("maxMarks", 10),
                Map.entry("allowedExtensions", "pdf, exe"), Map.entry("maxFileSizeMb", 5)), t.session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TYPES_UNSUPPORTED"));
        postJson("/api/assignments", Map.ofEntries(Map.entry("classId", c), Map.entry("title", "Bad"), Map.entry("description", "d"),
                Map.entry("category", "ASSIGNMENT"), Map.entry("deadline", inDays(1)), Map.entry("maxMarks", 10),
                Map.entry("allowedExtensions", "pdf"), Map.entry("maxFileSizeMb", 5),
                Map.entry("rubric", java.util.List.of(Map.of("title", "A", "maxMarks", 4)))), t.session())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RUBRIC_INVALID"));
    }

    @Test
    void newAssignmentNotifiesEnrolledStudents() throws Exception {
        Setup x = setup("newnotif", true, false);
        assertThat(ok(getJson("/api/notifications", x.student().session())).findValuesAsText("type")).contains("NEW_ASSIGNMENT");
    }
}
