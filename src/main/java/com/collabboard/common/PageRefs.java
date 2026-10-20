package com.collabboard.common;

/** Frontend hash routes used as notification links. */
public final class PageRefs {

    private PageRefs() {
    }

    public static String classroom(long id) {
        return "#/classes/" + id;
    }

    public static String assignment(long id) {
        return "#/assignments/" + id;
    }

    public static String project(long id) {
        return "#/projects/" + id;
    }

    public static String evaluation(long submissionId) {
        return "#/evaluation/" + submissionId;
    }

    public static String invitations() {
        return "#/projects";
    }
}
