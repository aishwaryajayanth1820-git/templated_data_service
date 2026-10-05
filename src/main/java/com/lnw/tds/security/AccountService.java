package com.lnw.tds.security;

import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lnw.tds.web.ApiException;
import com.lnw.tds.web.ValidationException;

/** Account operations shared by the auth endpoints, the bootstrap and (later) user administration. */
@Service
public class AccountService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    AccountService(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    /** Verifies the current password, applies the policy and clears {@code must_change_password}. */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        UserAccount account = users.findByUsername(username).orElseThrow(() -> ApiException.notFound("User " + username));
        if (!encoder.matches(currentPassword, account.passwordHash())) {
            throw ValidationException.field("currentPassword", "The current password is not correct");
        }
        if (currentPassword.equals(newPassword)) {
            throw ValidationException.field("newPassword", "Choose a password different from the current one");
        }
        PasswordPolicy.check(username, newPassword).ifPresent(reason -> {
            throw ValidationException.field("newPassword", reason);
        });
        users.updatePassword(account.id(), encoder.encode(newPassword), false);
    }

    /** Creates a user with the given roles. {@code viewer} is implicit and never stored. */
    @Transactional
    public long createUser(String username, String displayName, String initialPassword, Set<String> roleNames,
                           boolean mustChangePassword) {
        if (users.findByUsername(username).isPresent()) {
            throw ApiException.conflict(com.lnw.tds.web.ErrorCode.CONFLICT_UNIQUE, "User " + username + " already exists");
        }
        long id = users.insert(username, encoder.encode(initialPassword), displayName, mustChangePassword);
        for (String name : roleNames) {
            if (Roles.VIEWER.equals(name)) {
                continue;
            }
            Role role = roles.findByName(name).orElseThrow(() -> ApiException.notFound("Role " + name));
            users.addRole(id, role.id());
        }
        return id;
    }

    /** A user as the admin screens see it (no password hash). */
    public record UserView(String username, String displayName, boolean enabled, boolean mustChangePassword,
                           java.util.List<String> roles, java.time.Instant createdAt) {}

    public java.util.List<UserView> listUsers() {
        return users.findAll().stream().map(this::view).toList();
    }

    public UserView user(String username) {
        return view(require(username));
    }

    /** Updates profile and roles. Admins cannot disable themselves or drop their own admin role (no lock-out). */
    @Transactional
    public UserView updateUser(String username, String displayName, boolean enabled, Set<String> roleNames, String actingUser) {
        UserAccount account = require(username);
        if (username.equals(actingUser) && (!enabled || !roleNames.contains(Roles.ADMIN))) {
            throw ApiException.conflict(com.lnw.tds.web.ErrorCode.CONFLICT_REFERENCE,
                    "You cannot disable yourself or remove your own admin role");
        }
        users.updateProfile(account.id(), displayName, enabled);
        users.clearRoles(account.id());
        for (String name : roleNames) {
            if (Roles.VIEWER.equals(name)) {
                continue;
            }
            Role role = roles.findByName(name).orElseThrow(() -> ApiException.notFound("Role " + name));
            users.addRole(account.id(), role.id());
        }
        return user(username);
    }

    /** Sets a temporary password; the user must change it at next sign-in. */
    @Transactional
    public void resetPassword(String username, String temporaryPassword) {
        UserAccount account = require(username);
        PasswordPolicy.check(username, temporaryPassword).ifPresent(reason -> {
            throw ValidationException.field("password", reason);
        });
        users.updatePassword(account.id(), encoder.encode(temporaryPassword), true);
    }

    /** Validates and creates a user who must change the initial password at first sign-in. */
    @Transactional
    public UserView createUserChecked(String username, String displayName, String password, Set<String> roleNames) {
        if (username == null || !username.matches("^[A-Za-z0-9._@-]{1,100}$")) {
            throw ValidationException.field("username", "Use 1–100 letters, digits or . _ @ -");
        }
        PasswordPolicy.check(username, password).ifPresent(reason -> {
            throw ValidationException.field("password", reason);
        });
        createUser(username, displayName, password, roleNames, true);
        return user(username);
    }

    private UserView view(UserAccount a) {
        java.util.List<String> r = new java.util.ArrayList<>(users.roleNames(a.id()));
        r.add(Roles.VIEWER);
        return new UserView(a.username(), a.displayName(), a.enabled(), a.mustChangePassword(),
                r.stream().sorted().distinct().toList(), a.createdAt());
    }

    long userCount() {
        return users.count();
    }

    UserAccount require(String username) {
        return users.findByUsername(username).orElseThrow(() -> ApiException.notFound("User " + username));
    }

    Set<String> roleNames(long userId) {
        return users.roleNames(userId);
    }
}
