package io.github.aishwaryajayanth1820.tds.security;

import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** JSON login for the SPA. Logout ({@code POST /api/auth/logout}) is handled by Spring Security. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contexts;
    private final AccountService accounts;
    private final UserDetailsService userDetails;

    AuthController(AuthenticationManager authenticationManager, SessionAuthenticationStrategy sessionStrategy,
                   SecurityContextRepository contexts, AccountService accounts, TdsUserDetailsService userDetails) {
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.contexts = contexts;
        this.accounts = accounts;
        this.userDetails = userDetails;
    }

    /** Touching the token makes Spring Security write the {@code XSRF-TOKEN} cookie. */
    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        sessionStrategy.onAuthentication(auth, request, response);
        storeAuthentication(auth, request, response);
        touchCsrfToken(request);
        return MeResponse.of(((TdsUserDetails) auth.getPrincipal()).principal());
    }

    @GetMapping("/me")
    MeResponse me() {
        return MeResponse.of(CurrentPrincipal.get());
    }

    /** Changes the caller's password and refreshes the session so the forced-change flag clears immediately. */
    @PostMapping("/password")
    MeResponse changePassword(@Valid @RequestBody PasswordChangeRequest body, HttpServletRequest request,
                              HttpServletResponse response) {
        String username = CurrentPrincipal.get().username();
        accounts.changePassword(username, body.currentPassword(), body.newPassword());
        TdsUserDetails refreshed = (TdsUserDetails) userDetails.loadUserByUsername(username);
        refreshed.eraseCredentials();
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(refreshed, null, refreshed.getAuthorities());
        storeAuthentication(auth, request, response);
        return MeResponse.of(refreshed.principal());
    }

    /**
     * Login rotates the CSRF token lazily; reading it now makes the new {@code XSRF-TOKEN}
     * cookie part of the login response, so the SPA's next write carries a valid token.
     */
    private static void touchCsrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }

    private void storeAuthentication(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    record CsrfResponse(String headerName, String token) {}

    record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    record PasswordChangeRequest(@NotBlank String currentPassword,
                                 @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String newPassword) {}

    record MeResponse(String username, String displayName, List<String> roles, boolean mustChangePassword) {
        static MeResponse of(TdsPrincipal p) {
            return new MeResponse(p.username(), p.displayName(), List.copyOf(p.roles()), p.mustChangePassword());
        }
    }
}
