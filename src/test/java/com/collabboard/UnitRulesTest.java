package com.collabboard;

import com.collabboard.classroom.Classroom;
import com.collabboard.storage.FileTypes;
import com.collabboard.submission.SubmissionPolicy;
import com.collabboard.submission.SubmissionStatus;
import com.collabboard.submission.WorkItem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests for the submission deadline policy and file type rules. */
class UnitRulesTest {

    private static WorkItem item(Instant deadline, boolean resubmit, boolean late) {
        return new WorkItem() {
            public Long getId() { return 1L; }
            public Classroom getClassroom() { return null; }
            public String getTitle() { return "t"; }
            public Instant getDeadline() { return deadline; }
            public int getMaxMarks() { return 10; }
            public String getAllowedExtensions() { return "pdf"; }
            public int getMaxFileSizeMb() { return 5; }
            public boolean isAllowResubmission() { return resubmit; }
            public boolean isAllowLate() { return late; }
        };
    }

    private final Instant now = Instant.now();
    private final Instant future = now.plus(1, ChronoUnit.DAYS);
    private final Instant past = now.minus(1, ChronoUnit.DAYS);

    @Test
    void firstSubmissionBeforeDeadlineIsAllowedAndOnTime() {
        var d = SubmissionPolicy.evaluate(item(future, false, false), SubmissionStatus.NOT_SUBMITTED, 0, now);
        assertThat(d.allowed()).isTrue();
        assertThat(d.late()).isFalse();
    }

    @Test
    void firstSubmissionAfterDeadlineDependsOnLatePolicy() {
        assertThat(SubmissionPolicy.evaluate(item(past, true, false), null, 0, now).allowed()).isFalse();
        var late = SubmissionPolicy.evaluate(item(past, true, true), null, 0, now);
        assertThat(late.allowed()).isTrue();
        assertThat(late.late()).isTrue();
    }

    @Test
    void resubmissionRules() {
        assertThat(SubmissionPolicy.evaluate(item(future, true, false), SubmissionStatus.SUBMITTED, 1, now).allowed()).isTrue();
        assertThat(SubmissionPolicy.evaluate(item(future, false, false), SubmissionStatus.SUBMITTED, 1, now).allowed()).isFalse();
        assertThat(SubmissionPolicy.evaluate(item(past, true, true), SubmissionStatus.LATE, 1, now).allowed()).isFalse();
    }

    @Test
    void finalAndRevisionStates() {
        assertThat(SubmissionPolicy.evaluate(item(future, true, true), SubmissionStatus.EVALUATED, 1, now).allowed()).isFalse();
        assertThat(SubmissionPolicy.evaluate(item(future, true, true), SubmissionStatus.REJECTED, 1, now).allowed()).isFalse();
        var revision = SubmissionPolicy.evaluate(item(past, false, false), SubmissionStatus.REVISION_REQUESTED, 1, now);
        assertThat(revision.allowed()).isTrue();
        assertThat(revision.late()).isFalse();
    }

    @Test
    void fileSignaturesAreChecked() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        assertThat(FileTypes.contentMatches("pdf", pdf, pdf.length)).isTrue();
        byte[] exe = new byte[]{0x4D, 0x5A, 0, 0};
        assertThat(FileTypes.contentMatches("pdf", exe, exe.length)).isFalse();
        assertThat(FileTypes.contentMatches("docx", new byte[]{0x50, 0x4B, 0x03, 0x04}, 4)).isTrue();
        assertThat(FileTypes.contentMatches("txt", new byte[]{'a', 0, 'b'}, 3)).isFalse();
        assertThat(FileTypes.contentMatches("exe", exe, exe.length)).isFalse();
    }

    @Test
    void allowedExtensionListIsNormalisedAndValidated() {
        assertThat(FileTypes.normalizeAllowed(" .PDF, docx ,zip")).isEqualTo("pdf,docx,zip");
        assertThatThrownBy(() -> FileTypes.normalizeAllowed("pdf,exe")).hasMessageContaining("exe");
        assertThatThrownBy(() -> FileTypes.normalizeAllowed(" , ")).hasMessageContaining("at least one");
    }
}
