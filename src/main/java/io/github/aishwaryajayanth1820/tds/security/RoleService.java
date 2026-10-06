package io.github.aishwaryajayanth1820.tds.security;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.aishwaryajayanth1820.tds.web.ApiException;
import io.github.aishwaryajayanth1820.tds.web.ErrorCode;
import io.github.aishwaryajayanth1820.tds.web.ValidationException;

/** Role management (ADR-0008). Built-in roles cannot be removed; roles held by users cannot be removed. */
@Service
public class RoleService {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{0,40}$");

    private final RoleRepository roles;

    RoleService(RoleRepository roles) {
        this.roles = roles;
    }

    public List<Role> list() {
        return roles.findAll();
    }

    public Set<String> names() {
        return roles.findAll().stream().map(Role::name).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    public long userCount(String name) {
        return roles.countUsers(require(name).id());
    }

    @Transactional
    public Role create(String name, String description) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw ValidationException.field("name", "Use lower_snake_case: a letter, then letters, digits or _ (max 41)");
        }
        if (roles.findByName(name).isPresent()) {
            throw ApiException.conflict(ErrorCode.CONFLICT_UNIQUE, "Role " + name + " already exists");
        }
        roles.insert(name, description);
        return require(name);
    }

    @Transactional
    public Role update(String name, String description) {
        Role r = require(name);
        roles.updateDescription(r.id(), description);
        return require(name);
    }

    /** {@code templatesUsing} is supplied by the caller (admin layer), which knows the catalog. */
    @Transactional
    public void delete(String name, List<String> templatesUsing) {
        Role r = require(name);
        if (r.builtin()) {
            throw ApiException.conflict(ErrorCode.CONFLICT_REFERENCE, "Built-in role " + name + " cannot be deleted");
        }
        long users = roles.countUsers(r.id());
        if (users > 0) {
            throw ApiException.conflict(ErrorCode.CONFLICT_REFERENCE, "Role " + name + " is assigned to " + users + " user(s)");
        }
        if (!templatesUsing.isEmpty()) {
            throw ApiException.conflict(ErrorCode.CONFLICT_REFERENCE,
                    "Role " + name + " is used by template(s) " + String.join(", ", templatesUsing));
        }
        roles.delete(r.id());
    }

    Role require(String name) {
        return roles.findByName(name).orElseThrow(() -> ApiException.notFound("Role " + name));
    }
}
