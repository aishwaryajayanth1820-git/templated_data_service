package io.github.aishwaryajayanth1820.tds.grammar;

import java.util.Optional;
import java.util.Set;

import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;

/** What semantic validation needs to know about the world outside one template (ports, application design §5.2). */
public record ValidationContext(TemplateLookup templates, Set<String> roles, ScriptCatalog scripts) {

    /** Other templates by name: the draft (or published) model, and whether it is published. */
    public interface TemplateLookup {
        Optional<Template> find(String name);

        boolean isPublished(String name);
    }

    /** Implemented by the script registry; keeps the grammar free of the script engine. */
    public interface ScriptCatalog {
        boolean exists(String scriptPath);

        boolean hasFunction(String scriptPath, String function);
    }
}
