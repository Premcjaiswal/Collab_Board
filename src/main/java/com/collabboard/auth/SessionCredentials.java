package com.collabboard.auth;

import com.collabboard.user.User;
import com.collabboard.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Ties each signed-in session to the credentials it was created with. The stamp is a digest of the password hash,
 * which changes on every password change or reset (BCrypt uses a fresh salt), so other sessions of that account stop
 * being accepted, as do sessions of deactivated accounts. Nothing secret is stored in the session.
 */
@Component
public class SessionCredentials {

    static final String STAMP_ATTRIBUTE = SessionCredentials.class.getName() + ".STAMP";

    private final UserRepository users;

    public SessionCredentials(UserRepository users) {
        this.users = users;
    }

    /** Records the user's current credentials in the request's session (after login or the user's own password change). */
    public void bind(HttpServletRequest request, Long userId) {
        users.findById(userId).ifPresent(u -> request.getSession().setAttribute(STAMP_ATTRIBUTE, stamp(u)));
    }

    /** True when the session was created with the account's current password and the account is still active. */
    public boolean isCurrent(HttpSession session, Long userId) {
        Object bound = session.getAttribute(STAMP_ATTRIBUTE);
        if (bound == null) {
            return false;
        }
        Optional<User> user = users.findById(userId);
        return user.isPresent() && user.get().isActive() && bound.equals(stamp(user.get()));
    }

    private static String stamp(User user) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(user.getPasswordHash().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
