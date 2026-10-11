package io.github.aishwaryajayanth1820.tds.security;

import java.io.Serializable;
import java.util.Set;
import java.util.TreeSet;

/** The authenticated user as the rest of the application sees it. {@code roles} always contains {@code viewer}. */
public record TdsPrincipal(String username, String displayName, Set<String> roles, boolean mustChangePassword)
        implements Serializable {

    public TdsPrincipal {
        TreeSet<String> all = new TreeSet<>(roles);
        all.add(Roles.VIEWER);
        roles = java.util.Collections.unmodifiableSortedSet(all);
    }

    public boolean isAdmin() {
        return roles.contains(Roles.ADMIN);
    }
}
