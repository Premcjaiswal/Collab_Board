package com.collabboard.common;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class Texts {

    private Texts() {
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Parses ".pdf, DOCX ,zip" into a normalised set {pdf, docx, zip}. */
    public static Set<String> parseExtensions(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .map(s -> s.startsWith(".") ? s.substring(1) : s)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String joinExtensions(Set<String> exts) {
        return String.join(",", exts);
    }

    private static final java.util.regex.Pattern GITHUB_REPO =
            java.util.regex.Pattern.compile("^https://(www\\.)?github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?/?$");

    /** Returns the trimmed URL, null for blank input, or throws when it is not a GitHub repository URL. */
    public static String normalizeGithubUrl(String url) {
        String u = trimToNull(url);
        if (u == null) {
            return null;
        }
        if (u.length() > 300 || !GITHUB_REPO.matcher(u).matches()) {
            throw ApiException.badRequest("INVALID_GITHUB_URL",
                    "Enter a GitHub repository URL like https://github.com/owner/repository.");
        }
        return u;
    }
}
