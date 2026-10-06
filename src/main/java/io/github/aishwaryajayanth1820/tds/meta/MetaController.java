package io.github.aishwaryajayanth1820.tds.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.aishwaryajayanth1820.tds.access.AccessEvaluator;
import io.github.aishwaryajayanth1820.tds.catalog.CatalogService;
import io.github.aishwaryajayanth1820.tds.grammar.Model.ActionSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.FieldOp;
import io.github.aishwaryajayanth1820.tds.grammar.Model.ManageType;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;
import io.github.aishwaryajayanth1820.tds.security.CurrentPrincipal;
import io.github.aishwaryajayanth1820.tds.security.TdsPrincipal;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Per-user UI metadata the React app renders from (architecture §8.1). Fields a user cannot read and actions
 * they cannot run are never sent.
 */
@RestController
@RequestMapping("/api/meta")
class MetaController {

    record NavItem(String name, String label, String description, ManageType manageType) {}

    record Navigation(long generation, List<NavItem> tables, List<NavItem> dataSources) {}

    private final CatalogService catalog;
    private final AccessEvaluator access;
    private final JsonMapper mapper;

    MetaController(CatalogService catalog, AccessEvaluator access, JsonMapper mapper) {
        this.catalog = catalog;
        this.access = access;
        this.mapper = mapper;
    }

    @GetMapping
    Navigation navigation() {
        TdsPrincipal p = CurrentPrincipal.get();
        var snapshot = catalog.snapshot();
        List<NavItem> tables = new ArrayList<>();
        List<NavItem> sources = new ArrayList<>();
        for (CatalogService.Published pub : snapshot.templates().values()) {
            Template t = pub.model();
            if (!access.can(t, p, AccessEvaluator.READ)) {
                continue;
            }
            NavItem item = new NavItem(t.name(), t.displayLabel(), t.description(), t.manageType());
            if (t.manageType() == ManageType.DATA_SOURCE) {
                sources.add(item);
            } else {
                tables.add(item);
            }
        }
        tables.sort(java.util.Comparator.comparing(NavItem::label, String.CASE_INSENSITIVE_ORDER));
        sources.sort(java.util.Comparator.comparing(NavItem::label, String.CASE_INSENSITIVE_ORDER));
        return new Navigation(snapshot.generation(), tables, sources);
    }

    @GetMapping("/{name}")
    ObjectNode template(@PathVariable String name) {
        TdsPrincipal p = CurrentPrincipal.get();
        CatalogService.Published pub = catalog.require(name);
        Template t = pub.model();
        access.require(t, p, AccessEvaluator.READ);
        ObjectNode n = mapper.createObjectNode();
        n.put("name", t.name());
        n.put("label", t.displayLabel());
        if (t.description() != null) {
            n.put("description", t.description());
        }
        n.put("manageType", t.manageType().name());
        n.put("version", pub.version());
        n.put("rowVersion", t.hasRowVersion());
        ArrayNode fields = n.putArray("fields");
        for (Field f : t.fields()) {
            Set<FieldOp> ops = access.fieldOps(t, f, p);
            if (ops.isEmpty()) {
                continue;
            }
            ObjectNode fj = (ObjectNode) f.json().deepCopy();
            fj.remove("access");
            ArrayNode o = fj.putArray("ops");
            ops.forEach(op -> o.add(op.name().toLowerCase(java.util.Locale.ROOT)));
            fields.add(fj);
        }
        ArrayNode actions = n.putArray("actions");
        for (ActionSpec a : t.actions()) {
            if (access.canRun(t, p, a.name())) {
                ObjectNode aj = (ObjectNode) a.json().deepCopy();
                aj.remove("script");
                aj.remove("function");
                actions.add(aj);
            }
        }
        n.set("view", t.json().has("view") ? t.json().get("view").deepCopy() : mapper.createObjectNode());
        n.set("rules", t.json().has("rules") ? t.json().get("rules").deepCopy() : mapper.createArrayNode());
        ObjectNode perms = n.putObject("permissions");
        for (String op : List.of(AccessEvaluator.READ, AccessEvaluator.CREATE, AccessEvaluator.UPDATE, AccessEvaluator.DELETE)) {
            perms.put(op, access.can(t, p, op));
        }
        return n;
    }
}
