package io.github.aishwaryajayanth1820.tds.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.aishwaryajayanth1820.tds.catalog.DraftService;
import io.github.aishwaryajayanth1820.tds.security.Role;
import io.github.aishwaryajayanth1820.tds.security.RoleService;

import tools.jackson.databind.JsonNode;

/** Roles admin (ADR-0008). A role cannot be deleted while users hold it or templates reference it. */
@RestController
@RequestMapping("/api/admin/roles")
class RoleAdminController {

    record RoleView(String name, String description, boolean builtin, long users, List<String> templates) {}

    record RoleRequest(String name, String description) {}

    private final RoleService roles;
    private final DraftService drafts;

    RoleAdminController(RoleService roles, DraftService drafts) {
        this.roles = roles;
        this.drafts = drafts;
    }

    @GetMapping
    List<RoleView> list() {
        List<DraftService.DraftView> templates = drafts.list();
        return roles.list().stream().map(r -> view(r, templates)).toList();
    }

    @PostMapping
    RoleView create(@RequestBody RoleRequest body) {
        return view(roles.create(body.name(), body.description()), drafts.list());
    }

    @PutMapping("/{name}")
    RoleView update(@PathVariable String name, @RequestBody RoleRequest body) {
        return view(roles.update(name, body.description()), drafts.list());
    }

    @DeleteMapping("/{name}")
    ResponseEntity<Void> delete(@PathVariable String name) {
        roles.delete(name, templatesUsing(name, drafts.list()));
        return ResponseEntity.noContent().build();
    }

    private RoleView view(Role r, List<DraftService.DraftView> templates) {
        return new RoleView(r.name(), r.description(), r.builtin(), roles.userCount(r.name()), templatesUsing(r.name(), templates));
    }

    /** Templates whose table- or field-level access mentions the role. */
    static List<String> templatesUsing(String role, List<DraftService.DraftView> templates) {
        List<String> out = new ArrayList<>();
        for (DraftService.DraftView t : templates) {
            JsonNode json = t.json();
            boolean used = json.path("access").has(role);
            for (JsonNode f : json.path("fields").values()) {
                used |= f.path("access").has(role);
            }
            if (used) {
                out.add(t.name());
            }
        }
        return out;
    }
}
