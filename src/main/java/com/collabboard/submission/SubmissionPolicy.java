package com.collabboard.submission;

import java.time.Instant;

/**
 * The single source of truth for when work may be (re)submitted.
 *
 * <ul>
 *   <li>First submission: allowed until the deadline; afterwards only if late submissions are allowed (marked LATE).</li>
 *   <li>Resubmission (status SUBMITTED/LATE): only if the item allows resubmission and the deadline has not passed.</li>
 *   <li>REVISION_REQUESTED: resubmission is always open (the teacher explicitly asked for changes).</li>
 *   <li>EVALUATED and REJECTED are final; no further submissions.</li>
 * </ul>
 */
public final class SubmissionPolicy {

    private SubmissionPolicy() {
    }

    public record Decision(boolean allowed, boolean late, String reason) {
        static Decision allow(boolean late) {
            return new Decision(true, late, null);
        }

        static Decision deny(String reason) {
            return new Decision(false, false, reason);
        }
    }

    public static Decision evaluate(WorkItem item, SubmissionStatus status, int versions, Instant now) {
        boolean pastDeadline = now.isAfter(item.getDeadline());
        SubmissionStatus s = status == null ? SubmissionStatus.NOT_SUBMITTED : status;
        return switch (s) {
            case REJECTED -> Decision.deny("This submission was rejected. Further submissions are not accepted.");
            case EVALUATED -> Decision.deny("This submission has already been evaluated.");
            case REVISION_REQUESTED -> Decision.allow(false);
            case NOT_SUBMITTED -> versions > 0 ? resubmission(item, pastDeadline) : firstSubmission(item, pastDeadline);
            case SUBMITTED, LATE -> resubmission(item, pastDeadline);
        };
    }

    private static Decision firstSubmission(WorkItem item, boolean pastDeadline) {
        if (!pastDeadline) {
            return Decision.allow(false);
        }
        return item.isAllowLate()
                ? Decision.allow(true)
                : Decision.deny("The deadline has passed and late submissions are not accepted.");
    }

    private static Decision resubmission(WorkItem item, boolean pastDeadline) {
        if (!item.isAllowResubmission()) {
            return Decision.deny("Resubmission is not allowed for this work.");
        }
        if (pastDeadline) {
            return Decision.deny("The deadline has passed; resubmission is closed.");
        }
        return Decision.allow(false);
    }
}
