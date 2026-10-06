package io.github.aishwaryajayanth1820.tds.admin;

import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.aishwaryajayanth1820.tds.security.AccountService;
import io.github.aishwaryajayanth1820.tds.security.CurrentPrincipal;

/** Users admin: create, enable/disable, reset password, assign roles (requirement: roles assigned by admin). */
@RestController
@RequestMapping("/api/admin/users")
class UserAdminController {

    record CreateRequest(String username, String displayName, String password, List<String> roles) {}

    record UpdateRequest(String displayName, boolean enabled, List<String> roles) {}

    record PasswordRequest(String password) {}

    private final AccountService accounts;

    UserAdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    List<AccountService.UserView> list() {
        return accounts.listUsers();
    }

    @PostMapping
    AccountService.UserView create(@RequestBody CreateRequest body) {
        return accounts.createUserChecked(body.username(), body.displayName(), body.password(), roles(body.roles()));
    }

    @PutMapping("/{username}")
    AccountService.UserView update(@PathVariable String username, @RequestBody UpdateRequest body) {
        return accounts.updateUser(username, body.displayName(), body.enabled(), roles(body.roles()),
                CurrentPrincipal.get().username());
    }

    @PostMapping("/{username}/password")
    ResponseEntity<Void> resetPassword(@PathVariable String username, @RequestBody PasswordRequest body) {
        accounts.resetPassword(username, body.password());
        return ResponseEntity.noContent().build();
    }

    private static Set<String> roles(List<String> roles) {
        return roles == null ? Set.of() : Set.copyOf(roles);
    }
}
