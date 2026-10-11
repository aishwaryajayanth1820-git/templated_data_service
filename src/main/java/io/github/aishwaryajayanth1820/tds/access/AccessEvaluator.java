package io.github.aishwaryajayanth1820.tds.access;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.aishwaryajayanth1820.tds.grammar.FieldType;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.FieldOp;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;
import io.github.aishwaryajayanth1820.tds.security.Roles;
import io.github.aishwaryajayanth1820.tds.security.TdsPrincipal;
import io.github.aishwaryajayanth1820.tds.web.ApiException;

/**
 * Who may do what (grammar §6, ADR-0008): {@code admin} may do everything; otherwise the union over the
 * user's roles of table operations, narrowed per field by field operations.
 */
@Component
public class AccessEvaluator {

    public static final String READ = "read";
    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";

    public Set<String> tableOps(Template t, TdsPrincipal p) {
        Set<String> ops = new LinkedHashSet<>();
        if (p.isAdmin()) {
            ops.addAll(List.of(READ, CREATE, UPDATE, DELETE, "run:*"));
            return ops;
        }
        for (String role : p.roles()) {
            ops.addAll(t.effectiveAccess().getOrDefault(role, Set.of()));
        }
        return ops;
    }

    public boolean can(Template t, TdsPrincipal p, String op) {
        return tableOps(t, p).contains(op);
    }

    public boolean canRun(Template t, TdsPrincipal p, String action) {
        Set<String> ops = tableOps(t, p);
        return ops.contains("run:*") || ops.contains("run:" + action);
    }

    public void require(Template t, TdsPrincipal p, String op) {
        if (!can(t, p, op)) {
            throw ApiException.forbidden("Your roles do not allow " + op + " on " + t.name());
        }
    }

    public Set<FieldOp> fieldOps(Template t, Field f, TdsPrincipal p) {
        Set<FieldOp> out = EnumSet.noneOf(FieldOp.class);
        if (f.type() == FieldType.ID) {
            if (can(t, p, READ)) {
                out.add(FieldOp.READ);
            }
            return out;
        }
        if (p.isAdmin()) {
            return EnumSet.allOf(FieldOp.class);
        }
        for (String role : p.roles()) {
            if (Roles.ADMIN.equals(role)) {
                continue;
            }
            Set<String> table = t.effectiveAccess().getOrDefault(role, Set.of());
            Set<FieldOp> field = f.access() != null && f.access().containsKey(role) ? f.access().get(role)
                    : EnumSet.allOf(FieldOp.class);
            for (FieldOp op : field) {
                if (table.contains(op.name().toLowerCase(java.util.Locale.ROOT))) {
                    out.add(op);
                }
            }
        }
        return out;
    }

    public List<Field> readableFields(Template t, TdsPrincipal p) {
        return t.fields().stream().filter(f -> fieldOps(t, f, p).contains(FieldOp.READ)).toList();
    }
}
