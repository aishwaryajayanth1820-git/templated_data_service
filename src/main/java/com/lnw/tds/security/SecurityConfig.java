package com.lnw.tds.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import com.lnw.tds.web.ErrorCode;
import com.lnw.tds.web.Problems;

/**
 * Session-based JSON authentication for the SPA (ADR-0008): cookie CSRF tokens, 401/403 as
 * ProblemDetail, no redirects, logout at {@code POST /api/auth/logout}.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, Problems problems, CsrfTokenRepository csrfTokens,
                                    SecurityContextRepository contexts) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole(Roles.ADMIN)
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .csrf(c -> c.spa().csrfTokenRepository(csrfTokens))
            .securityContext(c -> c.securityContextRepository(contexts))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                    problems.write(res, ErrorCode.UNAUTHENTICATED, "Not signed in", "Sign in to continue"))
                .accessDeniedHandler((req, res, ex) ->
                    problems.write(res, ErrorCode.FORBIDDEN, "Forbidden",
                        ex instanceof org.springframework.security.web.csrf.CsrfException
                            ? "Missing or invalid CSRF token" : "You do not have permission for this operation")))
            .logout(l -> l
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .addFilterBefore(new PasswordChangeRequiredFilter(problems), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Applied by the login endpoint: new session id (fixation protection) and a fresh CSRF token. */
    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(TdsUserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
