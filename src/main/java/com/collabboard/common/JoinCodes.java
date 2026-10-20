package com.collabboard.common;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

/** Random, human-friendly join codes (no ambiguous characters such as O/0 or I/1) for classes and teams. */
public final class JoinCodes {

    public static final int LENGTH = 8;
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodes() {
    }

    /** Generates a code for which {@code taken} is false. */
    public static String generate(Predicate<String> taken) {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
            String code = sb.toString();
            if (!taken.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique join code");
    }

    /** Accepts codes typed with spaces, hyphens or lower case. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
