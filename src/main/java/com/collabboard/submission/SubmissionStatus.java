package com.collabboard.submission;

public enum SubmissionStatus {
    /** Record exists (e.g. group workspace) but nothing has been submitted yet. */
    NOT_SUBMITTED,
    SUBMITTED,
    /** Submitted after the deadline when the item allows late submissions. */
    LATE,
    /** A published evaluation exists. */
    EVALUATED,
    /** The teacher asked for changes; resubmission is open even after the deadline. */
    REVISION_REQUESTED,
    /** The teacher rejected the work with a reason; final. */
    REJECTED
}
