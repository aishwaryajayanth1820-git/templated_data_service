package io.github.aishwaryajayanth1820.tds.grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON text → structural check (meta-schema) → {@link Template}. */
public final class TemplateParser {

    private final JsonMapper mapper;
    private final StructuralValidator structural;

    public TemplateParser(JsonMapper mapper, StructuralValidator structural) {
        this.mapper = mapper;
        this.structural = structural;
    }

    public record ParseResult(Optional<Template> template, JsonNode tree, List<Issue> issues) {
        public boolean ok() {
            return template.isPresent() && issues.stream().noneMatch(Issue::isError);
        }
    }

    public ParseResult parse(String json) {
        JsonNode tree;
        try {
            tree = mapper.readTree(json);
        } catch (JacksonException e) {
            return new ParseResult(Optional.empty(), null,
                    List.of(Issue.error("S000", "Invalid JSON: " + e.getOriginalMessage(), "")));
        }
        return parse(tree);
    }

    public ParseResult parse(JsonNode tree) {
        if (tree == null || !tree.isObject()) {
            return new ParseResult(Optional.empty(), tree, List.of(Issue.error("S000", "A template must be a JSON object", "")));
        }
        List<Issue> issues = new ArrayList<>(structural.validate(tree));
        if (!issues.isEmpty()) {
            return new ParseResult(Optional.empty(), tree, issues);
        }
        try {
            return new ParseResult(Optional.of(TemplateBinder.bind(tree)), tree, issues);
        } catch (RuntimeException e) {
            issues.add(Issue.error("S001", "Cannot read template: " + e.getMessage(), ""));
            return new ParseResult(Optional.empty(), tree, issues);
        }
    }
}
