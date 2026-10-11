package io.github.aishwaryajayanth1820.tds.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import io.github.aishwaryajayanth1820.tds.web.ApiException;
import io.github.aishwaryajayanth1820.tds.web.ErrorCode;

/** Access to the {@link TdsPrincipal} of the current request. */
public final class CurrentPrincipal {

    private CurrentPrincipal() {}

    public static Optional<TdsPrincipal> find() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public static TdsPrincipal get() {
        return find().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Not signed in", "Sign in first"));
    }

    static Optional<TdsPrincipal> from(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof TdsUserDetails d) {
            return Optional.of(d.principal());
        }
        return Optional.empty();
    }
}
