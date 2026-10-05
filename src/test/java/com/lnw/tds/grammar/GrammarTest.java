package com.lnw.tds.grammar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lnw.tds.grammar.Model.Template;
import com.lnw.tds.grammar.RecordValidator.WriteOp;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Parser, semantic validator and record validator against the seed templates (grammar §4–§11). */
class GrammarTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final TemplateParser parser = new TemplateParser(MAPPER, new StructuralValidator());
    private final SemanticValidator semantic = new SemanticValidator();

    private Template seed(String name) throws Exception {
        return parser.parse(Files.readString(Path.of("templates", name + ".json"))).template().orElseThrow();
    }

    private ObjectNode seedJson(String name) throws Exception {
        return (ObjectNode) MAPPER.readTree(Files.readString(Path.of("templates", name + ".json")));
    }

    private ValidationContext context(Template... others) {
        Map<String, Template> byName = new HashMap<>();
        for (Template t : others) {
            byName.put(t.name(), t);
        }
        return new ValidationContext(new ValidationContext.TemplateLookup() {
            public Optional<Template> find(String name) {
                return Optional.ofNullable(byName.get(name));
            }

            public boolean isPublished(String name) {
                return byName.containsKey(name);
            }
        }, Set.of("admin", "viewer"), new ValidationContext.ScriptCatalog() {
            public boolean exists(String p) {
                return p.equals("alerts/create_ticket.js");
            }

            public boolean hasFunction(String p, String f) {
                return exists(p) && f.equals("createTicket");
            }
        });
    }

    private List<String> codes(ObjectNode doc, Template... others) {
        var r = parser.parse(doc);
        if (r.template().isEmpty()) {
            return r.issues().stream().map(Issue::code).toList();
        }
        return semantic.validate(r.template().get(), context(others)).stream().map(Issue::code).toList();
    }

    @Test
    void seedTemplatesParseAndValidateClean() throws Exception {
        Template groups = seed("alert_groups");
        Template alerts = seed("alerts");
        Template settings = seed("operator_settings");
        assertThat(semantic.validate(groups, context())).isEmpty();
        assertThat(semantic.validate(alerts, context(groups))).isEmpty();
        assertThat(semantic.validate(settings, context())).isEmpty();
        assertThat(alerts.field("alert_group").orElseThrow().ref().target()).isEqualTo("alert_groups");
        assertThat(alerts.rules()).hasSize(1).first().isInstanceOf(Model.Rule.Requires.class);
        assertThat(alerts.actions().getFirst().function()).isEqualTo("createTicket");
    }

    @Test
    void reportsInvalidJsonAndStructuralErrorsWithPaths() throws Exception {
        assertThat(parser.parse("{ not json").issues()).extracting(Issue::code).containsExactly("S000");
        ObjectNode doc = seedJson("alerts");
        ((ObjectNode) doc.withArray("fields").get(3)).remove("values");
        var r = parser.parse(doc);
        assertThat(r.ok()).isFalse();
        assertThat(r.issues()).anySatisfy(i -> assertThat(i.path()).startsWith("fields[3]"));
    }

    @Test
    void semanticChecksCarryTheStudioCodes() throws Exception {
        Template groups = seed("alert_groups");

        ObjectNode dup = seedJson("alerts");
        dup.withArray("fields").add(dup.withArray("fields").get(1).deepCopy());
        assertThat(codes(dup, groups)).contains("E001");

        assertThat(codes(seedJson("alerts"))).contains("E010");

        ObjectNode badDefault = seedJson("alerts");
        ((ObjectNode) badDefault.withArray("fields").get(3)).put("default", "NOPE");
        assertThat(codes(badDefault, groups)).contains("E020");

        ObjectNode badView = seedJson("alerts");
        badView.withObject("view").withArray("filters").add("no_such_field");
        assertThat(codes(badView, groups)).contains("E030");

        ObjectNode badVar = seedJson("alerts");
        ((ObjectNode) badVar.withArray("fields").get(7)).set("requiredWhen", MAPPER.readTree("{\"==\":[{\"var\":\"ghost\"},1]}"));
        assertThat(codes(badVar, groups)).contains("E031");

        ObjectNode badOp = seedJson("alerts");
        ((ObjectNode) badOp.withArray("fields").get(7)).set("requiredWhen", MAPPER.readTree("{\"reduce\":[1,2]}"));
        assertThat(codes(badOp, groups)).contains("E032");

        ObjectNode badScript = seedJson("alerts");
        ((ObjectNode) badScript.withArray("actions").get(0)).put("function", "missing");
        assertThat(codes(badScript, groups)).contains("E040");

        ObjectNode badRun = seedJson("alerts");
        badRun.withObject("access").withArray("viewer").add("run:ghost");
        assertThat(codes(badRun, groups)).contains("E041");
    }

    @Test
    void recordValidatorAppliesRequiredWhenRulesAndConstraints() throws Exception {
        Template alerts = seed("alerts");
        RecordValidator v = new RecordValidator();
        Map<String, Object> rec = new HashMap<>(Map.of("alert_date", "2026-10-03T09:42:00.000Z", "alert_name", "x",
                "alert_type", "CRITICAL", "alert_group", 1L, "alert_ticket", "OPS-1"));

        var r = v.validate(alerts, rec, WriteOp.CREATE, "admin", Set.of("admin"));
        assertThat(r.fieldErrors()).containsEntry("alert_description", "Required")
                .containsEntry("alert_site", "An alert with a ticket must also have a site.");

        rec.put("alert_description", "d");
        rec.put("alert_site", "LON");
        rec.put("alert_type", "NOT_AN_OPTION");
        rec.put("alert_name", "y".repeat(300));
        r = v.validate(alerts, rec, WriteOp.CREATE, "admin", Set.of("admin"));
        assertThat(r.fieldErrors()).containsEntry("alert_type", "Not an allowed value")
                .containsEntry("alert_name", "At most 255 characters");

        Template settings = seed("operator_settings");
        var s = v.validate(settings, Map.of("operator_name", "  ", "jurisdictional_name", "UK", "min_spin_time", 61.0),
                WriteOp.CREATE, "admin", Set.of("admin"));
        assertThat(s.fieldErrors()).containsEntry("operator_name", "Required").containsEntry("min_spin_time", "Must be ≤ 60");
    }
}
