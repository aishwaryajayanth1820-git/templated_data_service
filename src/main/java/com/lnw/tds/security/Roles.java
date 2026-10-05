package com.lnw.tds.security;

/** Built-in roles (ADR-0008). */
public final class Roles {

    /** Super role: every operation, never narrowed by field access. */
    public static final String ADMIN = "admin";

    /** Held implicitly by every authenticated user; never stored per user. */
    public static final String VIEWER = "viewer";

    private Roles() {}

    public static String authority(String role) {
        return "ROLE_" + role;
    }
}
