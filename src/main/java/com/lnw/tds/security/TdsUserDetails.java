package com.lnw.tds.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Spring Security view of a {@link UserAccount}; stored in the HTTP session after login. */
final class TdsUserDetails implements UserDetails, CredentialsContainer {

    private final TdsPrincipal principal;
    private final boolean enabled;
    private String passwordHash;

    TdsUserDetails(UserAccount account, java.util.Set<String> roles) {
        this.principal = new TdsPrincipal(account.username(), account.displayName(), roles, account.mustChangePassword());
        this.enabled = account.enabled();
        this.passwordHash = account.passwordHash();
    }

    TdsPrincipal principal() {
        return principal;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = principal.roles().stream()
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority(Roles.authority(r))).toList();
        return list;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return principal.username();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
