package com.lnw.tds.catalog;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lnw.tds.grammar.FieldType;
import com.lnw.tds.grammar.Model.Field;
import com.lnw.tds.grammar.Model.Template;
import com.lnw.tds.grammar.TemplateParser;
import com.lnw.tds.grammar.ValidationContext;
import com.lnw.tds.web.ApiException;

/**
 * The published templates the service runs on (ADR-0007). An immutable snapshot is swapped in after every
 * publish; requests read it once and use it throughout.
 */
@Service
public class CatalogService implements ValidationContext.TemplateLookup {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    /** A published template and its version. */
    public record Published(Template model, int version) {
        public String name() {
            return model.name();
        }
    }

    public record Snapshot(long generation, Map<String, Published> templates) {
        public Optional<Published> find(String name) {
            return Optional.ofNullable(templates.get(name));
        }

        /** Template fields in other templates that reference {@code name}. */
        public List<String> incomingRefs(String name) {
            return templates.values().stream()
                    .flatMap(p -> p.model().fields().stream()
                            .filter(f -> f.type() == FieldType.REF && f.ref() != null && f.ref().target().equals(name))
                            .map(f -> p.name() + "." + f.name()))
                    .toList();
        }
    }

    private final TemplateRepository repository;
    private final TemplateParser parser;
    private volatile Snapshot snapshot = new Snapshot(0, Map.of());

    CatalogService(TemplateRepository repository, TemplateParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public Published require(String name) {
        return snapshot.find(name).orElseThrow(() -> ApiException.notFound("Table " + name));
    }

    public Collection<Published> all() {
        return snapshot.templates().values();
    }

    /** Rebuilds the snapshot from the published versions in the database. */
    public synchronized void reload() {
        Map<String, Published> next = new LinkedHashMap<>();
        repository.latestPublished().forEach((name, v) -> {
            var result = parser.parse(v.json());
            if (result.template().isPresent()) {
                next.put(name, new Published(result.template().get(), v.version()));
            } else {
                log.error("Published template {} v{} cannot be parsed and is skipped: {}", name, v.version(), result.issues());
            }
        });
        snapshot = new Snapshot(snapshot.generation() + 1, Map.copyOf(next));
        log.info("Catalog generation {}: {} published template(s) {}", snapshot.generation(), next.size(), next.keySet());
    }

    /** For validation: the draft model when it parses, else the published one. */
    @Override
    public Optional<Template> find(String name) {
        Optional<Template> draft = repository.find(name).flatMap(r -> parser.parse(r.draftJson()).template());
        return draft.isPresent() ? draft : snapshot.find(name).map(Published::model);
    }

    @Override
    public boolean isPublished(String name) {
        return snapshot.templates().containsKey(name);
    }

    public static Optional<Field> refField(Template t, String fieldName) {
        return t.field(fieldName).filter(f -> f.type() == FieldType.REF);
    }
}
