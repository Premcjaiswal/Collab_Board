package com.collabboard.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Signs out sessions whose credentials are no longer current (see {@link SessionCredentials}). The request then
 * continues unauthenticated, so protected endpoints answer 401. Registered only inside the security filter chain.
 *
 * <p>It runs for every request that carries a signed-in session rather than matching on the URL: the raw request URI
 * is not decoded (e.g. {@code /%61pi/...} reaches {@code /api/...}), so a path check could be sidestepped.
 */
public class SessionCredentialFilter extends OncePerRequestFilter {

    private final SessionCredentials credentials;

    public SessionCredentialFilter(SessionCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
                try {
                    if (!credentials.isCurrent(session, user.id())) {
                        session.invalidate();
                        SecurityContextHolder.clearContext();
                    }
                } catch (IllegalStateException alreadyInvalidated) {
                    // A parallel request (e.g. logout) invalidated the session meanwhile: treat it as signed out.
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }
}
