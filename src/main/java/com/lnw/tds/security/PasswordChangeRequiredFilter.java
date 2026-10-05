package com.lnw.tds.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import com.lnw.tds.web.ErrorCode;
import com.lnw.tds.web.Problems;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Until a forced password change is done, only {@code /api/auth/**} is reachable (ADR-0008). */
final class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final Problems problems;

    PasswordChangeRequiredFilter(Problems problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean guarded = path.startsWith("/api/") && !path.startsWith("/api/auth/");
        if (guarded && CurrentPrincipal.find().map(TdsPrincipal::mustChangePassword).orElse(false)) {
            problems.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED, "Password change required",
                    "Change your password before using the application");
            return;
        }
        chain.doFilter(request, response);
    }
}
