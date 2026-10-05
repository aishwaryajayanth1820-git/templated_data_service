package com.lnw.tds.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lnw.tds.grammar.Issue;
import com.lnw.tds.grammar.Model.Template;
import com.lnw.tds.grammar.SemanticValidator;
import com.lnw.tds.grammar.TemplateParser;
import com.lnw.tds.grammar.ValidationContext;
import com.lnw.tds.security.RoleService;
import com.lnw.tds.web.ApiException;
import com.lnw.tds.web.ErrorCode;
import com.lnw.tds.web.ValidationException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Editable template drafts (Schema Studio). A draft may be saved with errors; publishing requires none. */
@Service
public class DraftService {

    private static final Pattern NAME = Pattern.compile("^(?!tds_)[a-z][a-z0-9_]{0,62}$");

    public enum Status { DRAFT, PUBLISHED, CHANGED }

    public record DraftView(String name, String label, String manageType, JsonNode json, String checksum,
                            Integer publishedVersion, Status status, List<Issue> issues, Instant updatedAt, String updatedBy) {}

    private final TemplateRepository repository;
    private final CatalogService catalog;
    private final TemplateParser parser;
    private final SemanticValidator semantic;
    private final RoleService roles;
    private final ValidationContext.ScriptCatalog scripts;
    private final JsonMapper mapper;

    DraftService(TemplateRepository repository, CatalogService catalog, TemplateParser parser, SemanticValidator semantic,
                 RoleService roles, ValidationContext.ScriptCatalog scripts, JsonMapper mapper) {
        this.repository = repository;
        this.catalog = catalog;
        this.parser = parser;
        this.semantic = semantic;
        this.roles = roles;
        this.scripts = scripts;
        this.mapper = mapper;
    }

    public List<DraftView> list() {
        List<DraftView> out = new ArrayList<>();
        for (TemplateRepository.Row r : repository.findAll()) {
            out.add(view(r));
        }
        return out;
    }

    public boolean exists(String name) {
        return repository.find(name).isPresent();
    }

    public String readObjectName(String json) {
        return readObject(json).path("name").asString("");
    }

    public DraftView get(String name) {
        return view(require(name));
    }

    @Transactional
    public DraftView create(String json, String user) {
        ObjectNode tree = readObject(json);
        String name = tree.path("name").asString("");
        String manageType = tree.path("manageType").asString("");
        if (!NAME.matcher(name).matches()) {
            throw ValidationException.field("name", "Use snake_case (a letter, then letters, digits or _), not starting with tds_");
        }
        if (!List.of("VIEW", "MANAGE_VIEW", "DATA_SOURCE").contains(manageType)) {
            throw ValidationException.field("manageType", "Choose VIEW, MANAGE_VIEW or DATA_SOURCE");
        }
        if (repository.find(name).isPresent()) {
            throw ApiException.conflict(ErrorCode.CONFLICT_UNIQUE, "Template " + name + " already exists");
        }
        String text = pretty(tree);
        repository.insert(name, manageType, text, checksum(text), user);
        return get(name);
    }

    /** Saves a new draft text. {@code expectedChecksum} protects against overwriting someone else's edit. */
    @Transactional
    public DraftView save(String name, String json, String expectedChecksum, String user) {
        TemplateRepository.Row row = require(name);
        if (expectedChecksum != null && !expectedChecksum.equals(row.draftChecksum())) {
            throw ApiException.conflict(ErrorCode.CONFLICT_VERSION, "The draft was changed by someone else. Reload it first");
        }
        ObjectNode tree = readObject(json);
        if (!name.equals(tree.path("name").asString(""))) {
            throw ValidationException.field("name", "The name cannot change (it is " + name + ")");
        }
        String manageType = tree.path("manageType").asString("");
        if (!List.of("VIEW", "MANAGE_VIEW", "DATA_SOURCE").contains(manageType)) {
            throw ValidationException.field("manageType", "Choose VIEW, MANAGE_VIEW or DATA_SOURCE");
        }
        String text = pretty(tree);
        repository.updateDraft(name, manageType, text, checksum(text), user);
        return get(name);
    }

    @Transactional
    public void delete(String name) {
        TemplateRepository.Row row = require(name);
        if (row.publishedVersion() != null) {
            throw ApiException.conflict(ErrorCode.CONFLICT_REFERENCE,
                    "Template " + name + " is published; dropping published tables is not supported yet");
        }
        repository.delete(name);
    }

    public List<VersionView> versions(String name) {
        require(name);
        return repository.versions(name).stream()
                .map(v -> new VersionView(v.version(), v.publishedAt(), v.publishedBy(), v.appliedSql(), parseQuietly(v.planJson())))
                .toList();
    }

    public record VersionView(int version, Instant publishedAt, String publishedBy, String appliedSql, JsonNode plan) {}

    /** Structural + semantic issues for a draft text. */
    public Validated validate(String json) {
        var parsed = parser.parse(json);
        List<Issue> issues = new ArrayList<>(parsed.issues());
        parsed.template().ifPresent(t -> issues.addAll(semantic.validate(t, context())));
        return new Validated(parsed.template().orElse(null), issues);
    }

    public record Validated(Template template, List<Issue> issues) {
        public boolean hasErrors() {
            return template == null || issues.stream().anyMatch(Issue::isError);
        }
    }

    ValidationContext context() {
        return new ValidationContext(catalog, roles.names(), scripts);
    }

    TemplateRepository.Row require(String name) {
        return repository.find(name).orElseThrow(() -> ApiException.notFound("Template " + name));
    }

    private DraftView view(TemplateRepository.Row r) {
        Validated v = validate(r.draftJson());
        Status status = r.publishedVersion() == null ? Status.DRAFT
                : catalog.snapshot().find(r.name()).map(p -> sameAsPublished(r, p)).orElse(false) ? Status.PUBLISHED
                : Status.CHANGED;
        JsonNode json = parseQuietly(r.draftJson());
        return new DraftView(r.name(), json.path("label").asString(null), r.manageType(), json, r.draftChecksum(),
                r.publishedVersion(), status, v.issues(), r.updatedAt(), r.updatedBy());
    }

    private boolean sameAsPublished(TemplateRepository.Row r, CatalogService.Published p) {
        return parseQuietly(r.draftJson()).equals(p.model().json());
    }

    ObjectNode readObject(String json) {
        try {
            JsonNode n = mapper.readTree(json);
            if (n instanceof ObjectNode o) {
                return o;
            }
        } catch (JacksonException e) {
            throw ValidationException.field("json", "Invalid JSON: " + e.getOriginalMessage());
        }
        throw ValidationException.field("json", "A template must be a JSON object");
    }

    String pretty(JsonNode tree) {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
    }

    JsonNode parseQuietly(String json) {
        if (json == null) {
            return mapper.nullNode();
        }
        try {
            return mapper.readTree(json);
        } catch (JacksonException e) {
            return mapper.getNodeFactory().stringNode(json);
        }
    }

    static String checksum(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, Object> issuesProperty(List<Issue> issues) {
        return Map.of("issues", issues);
    }
}
