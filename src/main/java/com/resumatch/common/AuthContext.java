package com.resumatch.common;

import jakarta.servlet.http.HttpSession;

/**
 * Tiny session-backed auth helper. We avoid Spring Security to keep the
 * zero-cost stack simple, but still enforce roles per request.
 */
public final class AuthContext {
    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_ROLE = "role";
    public static final String SESSION_DISPLAY_NAME = "displayName";

    private AuthContext() {}

    public static Long userId(HttpSession session) {
        Object v = session.getAttribute(SESSION_USER_ID);
        if (v == null) throw new SecurityException("Not authenticated");
        return ((Number) v).longValue();
    }

    public static String role(HttpSession session) {
        Object v = session.getAttribute(SESSION_ROLE);
        if (v == null) throw new SecurityException("Not authenticated");
        return (String) v;
    }

    public static void requireRole(HttpSession session, String expected) {
        String r = role(session);
        if (!r.equals(expected) && !r.equals("ADMIN")) {
            throw new SecurityException("Forbidden: requires role " + expected);
        }
    }

    public static void login(HttpSession session, Long userId, String role, String displayName) {
        session.setAttribute(SESSION_USER_ID, userId);
        session.setAttribute(SESSION_ROLE, role);
        session.setAttribute(SESSION_DISPLAY_NAME, displayName);
    }

    public static void logout(HttpSession session) {
        session.invalidate();
    }
}
