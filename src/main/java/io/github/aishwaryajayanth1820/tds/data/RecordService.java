package io.github.aishwaryajayanth1820.tds.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import io.github.aishwaryajayanth1820.tds.access.AccessEvaluator;
import io.github.aishwaryajayanth1820.tds.catalog.CatalogService;
import io.github.aishwaryajayanth1820.tds.ddl.SqlDialect;
import io.github.aishwaryajayanth1820.tds.grammar.FieldType;
import io.github.aishwaryajayanth1820.tds.grammar.Model;
import io.github.aishwaryajayanth1820.tds.grammar.Model.DefaultValue;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.FieldOp;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;
import io.github.aishwaryajayanth1820.tds.grammar.RecordValidator;
import io.github.aishwaryajayanth1820.tds.grammar.RecordValidator.WriteOp;
import io.github.aishwaryajayanth1820.tds.security.TdsPrincipal;
import io.github.aishwaryajayanth1820.tds.web.ApiException;
import io.github.aishwaryajayanth1820.tds.web.ErrorCode;
import io.github.aishwaryajayanth1820.tds.web.ValidationException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Generic CRUD for every published template (architecture §8.2). Field-level permissions shape both
 * input (fields a role cannot set are rejected) and output (fields a role cannot read are never returned).
 */
@Service
public class RecordService {

    public record Page(List<ObjectNode> items, int page, int size, long total) {}

    public record Option(long id, String label) {}

    /** A row as canonical values (readable fields only) and display texts for ref and enum fields. */
    public record Loaded(Template template, Map<String, Object> values, Map<String, String> display) {}

    private final CatalogService catalog;
    private final AccessEvaluator access;
    private final ValueCodec codec;
    private final JdbcClient jdbc;
    private final RecordValidator validator;
    private final JsonMapper mapper;
    private final QueryBuilder sql;

    RecordService(CatalogService catalog, AccessEvaluator access, ValueCodec codec, SqlDialect dialect, JdbcClient jdbc,
                  RecordValidator validator, JsonMapper mapper) {
        this.catalog = catalog;
        this.access = access;
        this.codec = codec;
        this.jdbc = jdbc;
        this.validator = validator;
        this.mapper = mapper;
        this.sql = new QueryBuilder(dialect, codec);
    }

    public Page list(String table, MultiValueMap<String, String> params, TdsPrincipal p) {
        Template t = catalog.require(table).model();
        access.require(t, p, AccessEvaluator.READ);
        List<Field> readable = access.readableFields(t, p);
        Set<String> names = names(readable);
        RecordQuery q = RecordQuery.parse(params, t, names);
        List<Field> searchable = searchable(t, readable);
        QueryBuilder.SqlQuery count = sql.count(t, q, searchable);
        long total = jdbc.sql(count.sql()).params(count.params()).query(Long.class).single();
        QueryBuilder.SqlQuery select = sql.select(t, q, searchable);
        List<Map<String, Object>> rows = jdbc.sql(select.sql()).params(select.params()).query().listOfRows().stream()
                .map(r -> canonical(t, r)).toList();
        Map<String, Map<Long, String>> displays = refDisplays(t, readable, rows);
        List<ObjectNode> items = rows.stream().map(r -> toJson(t, r, readable, displays, p)).toList();
        return new Page(items, q.page(), q.size(), total);
    }

    public ObjectNode get(String table, long id, TdsPrincipal p) {
        Template t = catalog.require(table).model();
        access.require(t, p, AccessEvaluator.READ);
        Map<String, Object> row = load(t, id);
        List<Field> readable = access.readableFields(t, p);
        return toJson(t, row, readable, refDisplays(t, readable, List.of(row)), p);
    }

    @Transactional
    public ObjectNode create(String table, ObjectNode body, TdsPrincipal p) {
        Template t = catalog.require(table).model();
        access.require(t, p, AccessEvaluator.CREATE);
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : body.properties()) {
            Field f = writableField(t, e.getKey(), errors);
            if (f == null) {
                continue;
            }
            if (!access.fieldOps(t, f, p).contains(FieldOp.CREATE)) {
                if (!e.getValue().isNull()) {
                    errors.put(f.name(), "You cannot set this field");
                }
                continue;
            }
            decode(f, e.getValue(), values, errors);
        }
        for (Field f : t.fields()) {
            if (f.type() != FieldType.ID && !body.has(f.name()) && f.defaultValue() != null) {
                values.put(f.name(), defaultValue(f, p));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors, List.of());
        }
        validate(t, values, WriteOp.CREATE, p);
        Map<String, Object> db = toDb(t, values);
        if (t.hasAuditColumns()) {
            db.put("created_by", p.username());
        }
        long id = execute(t, DbErrorTranslator.Op.WRITE, () -> {
            QueryBuilder.SqlQuery insert = sql.insert(t, db);
            return jdbc.sql(insert.sql()).params(insert.params()).query(Long.class).single();
        });
        return get(table, id, p);
    }

    @Transactional
    public ObjectNode update(String table, long id, ObjectNode body, boolean partial, TdsPrincipal p) {
        Template t = catalog.require(table).model();
        access.require(t, p, AccessEvaluator.UPDATE);
        Map<String, Object> current = load(t, id);
        Map<String, String> errors = new LinkedHashMap<>();
        Long expectedVersion = null;
        if (t.hasRowVersion()) {
            JsonNode rv = body.get("row_version");
            if (rv == null || !rv.isNumber()) {
                errors.put("row_version", "Send the row_version you read (optimistic locking)");
            } else {
                expectedVersion = rv.asLong();
            }
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : body.properties()) {
            Field f = writableField(t, e.getKey(), errors);
            if (f == null) {
                continue;
            }
            Map<String, Object> one = new HashMap<>();
            decode(f, e.getValue(), one, errors);
            if (!one.containsKey(f.name())) {
                continue;
            }
            Object value = one.get(f.name());
            if (!access.fieldOps(t, f, p).contains(FieldOp.UPDATE)) {
                if (!Objects.equals(value, current.get(f.name()))) {
                    errors.put(f.name(), "You cannot change this field");
                }
                continue;
            }
            changes.put(f.name(), value);
        }
        if (!partial) {
            for (Field f : t.fields()) {
                if (f.type() != FieldType.ID && !body.has(f.name()) && access.fieldOps(t, f, p).contains(FieldOp.UPDATE)) {
                    changes.put(f.name(), null);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors, List.of());
        }
        Map<String, Object> merged = new HashMap<>(current);
        merged.putAll(changes);
        validate(t, merged, WriteOp.UPDATE, p);
        Long version = expectedVersion;
        int updated = execute(t, DbErrorTranslator.Op.WRITE, () -> {
            QueryBuilder.SqlQuery update = sql.update(t, id, version, toDb(t, changes), p.username());
            return jdbc.sql(update.sql()).params(update.params()).update();
        });
        if (updated == 0) {
            throw ApiException.conflict(ErrorCode.CONFLICT_VERSION, "This row was changed by someone else. Reload it and try again");
        }
        return get(table, id, p);
    }

    @Transactional
    public void delete(String table, long id, TdsPrincipal p) {
        Template t = catalog.require(table).model();
        access.require(t, p, AccessEvaluator.DELETE);
        int n = execute(t, DbErrorTranslator.Op.DELETE, () -> {
            QueryBuilder.SqlQuery delete = sql.delete(t, id);
            return jdbc.sql(delete.sql()).params(delete.params()).update();
        });
        if (n == 0) {
            throw ApiException.notFound("Row " + id + " of " + table);
        }
    }

    /** Choices for a {@code ref} field: id + display text of the target rows. */
    public List<Option> options(String table, String fieldName, String q, TdsPrincipal p) {
        Template t = catalog.require(table).model();
        access.require(t, p, AccessEvaluator.READ);
        Field f = t.field(fieldName).filter(x -> x.type() == FieldType.REF && x.ref() != null)
                .orElseThrow(() -> ApiException.notFound("Reference field " + fieldName));
        Template target = catalog.require(f.ref().target()).model();
        Field display = target.field(f.ref().display()).orElse(target.idField());
        QueryBuilder.SqlQuery options = sql.options(target, display.name(), q, 50);
        return jdbc.sql(options.sql()).params(options.params()).query().listOfRows().stream()
                .map(r -> new Option(((Number) r.get("id")).longValue(), String.valueOf(codec.fromDb(display, r.get("d")))))
                .toList();
    }

    /** Readable values of one row plus display texts, for action scripts ({@code ctx.record}, {@code ctx.display}). */
    public Loaded loadForAction(Template t, long id, TdsPrincipal p) {
        access.require(t, p, AccessEvaluator.READ);
        Map<String, Object> row = load(t, id);
        List<Field> readable = access.readableFields(t, p);
        Map<String, Object> values = new LinkedHashMap<>();
        readable.forEach(f -> values.put(f.name(), row.get(f.name())));
        Map<String, Map<Long, String>> refs = refDisplays(t, readable, List.of(row));
        Map<String, String> display = new LinkedHashMap<>();
        for (Field f : readable) {
            Object v = row.get(f.name());
            if (v == null) {
                continue;
            }
            if (f.type() == FieldType.ENUM) {
                display.put(f.name(), f.enumValue(v.toString()).map(Model.EnumValue::displayLabel).orElse(v.toString()));
            } else if (f.type() == FieldType.REF && refs.containsKey(f.name())) {
                display.put(f.name(), refs.get(f.name()).getOrDefault((Long) v, "#" + v));
            }
        }
        return new Loaded(t, values, display);
    }

    /** Inserts canonical values as-is, without app validation — used for seeding, like a direct DB insert. */
    @Transactional
    public long insertRaw(Template t, Map<String, Object> canonical, String user) {
        Map<String, Object> db = toDb(t, canonical);
        if (canonical.containsKey("id")) {
            db.put(t.idField().name(), canonical.get("id"));
        }
        if (t.hasAuditColumns()) {
            db.put("created_by", user);
        }
        QueryBuilder.SqlQuery insert = sql.insert(t, db);
        return jdbc.sql(insert.sql()).params(insert.params()).query(Long.class).single();
    }

    public long count(Template t) {
        return jdbc.sql("SELECT COUNT(*) FROM " + io.github.aishwaryajayanth1820.tds.ddl.SqlIdentifiers.quote(t.name())).query(Long.class).single();
    }

    private Map<String, Object> load(Template t, long id) {
        QueryBuilder.SqlQuery q = sql.byId(t, id);
        List<Map<String, Object>> rows = jdbc.sql(q.sql()).params(q.params()).query().listOfRows();
        if (rows.isEmpty()) {
            throw ApiException.notFound("Row " + id + " of " + t.name());
        }
        return canonical(t, rows.getFirst());
    }

    private Map<String, Object> canonical(Template t, Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field f : t.fields()) {
            out.put(f.name(), codec.fromDb(f, value(row, f.name())));
        }
        for (String a : Model.AUDIT_COLUMNS) {
            if (t.referenceableNames().contains(a)) {
                out.put(a, codec.auditFromDb(a, value(row, a)));
            }
        }
        return out;
    }

    private static Object value(Map<String, Object> row, String column) {
        if (row.containsKey(column)) {
            return row.get(column);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(column)) {
                return e.getValue();
            }
        }
        return null;
    }

    private ObjectNode toJson(Template t, Map<String, Object> row, List<Field> readable,
                              Map<String, Map<Long, String>> displays, TdsPrincipal p) {
        ObjectNode n = mapper.createObjectNode();
        Set<String> readableNames = names(readable);
        for (Field f : readable) {
            Object v = row.get(f.name());
            n.set(f.name(), codec.toJson(v));
            if (f.type() == FieldType.REF && v != null && displays.containsKey(f.name())) {
                String d = displays.get(f.name()).get((Long) v);
                if (d != null) {
                    n.put(f.name() + "$display", d);
                }
            }
        }
        for (String a : Model.AUDIT_COLUMNS) {
            if (row.containsKey(a)) {
                n.set(a, codec.toJson(row.get(a)));
            }
        }
        RecordValidator.Result check = validator.validate(t, row, WriteOp.UPDATE, p.username(), p.roles());
        if (!check.ok()) {
            ArrayNode issues = n.putArray("$issues");
            check.fieldErrors().forEach((k, v) -> {
                if (readableNames.contains(k)) {
                    issues.add(t.field(k).map(Field::displayLabel).orElse(k) + ": " + v);
                }
            });
            check.general().forEach(issues::add);
            if (issues.isEmpty()) {
                n.remove("$issues");
            }
        }
        return n;
    }

    private Map<String, Map<Long, String>> refDisplays(Template t, List<Field> readable, List<Map<String, Object>> rows) {
        Map<String, Map<Long, String>> out = new HashMap<>();
        for (Field f : readable) {
            if (f.type() != FieldType.REF || f.ref() == null) {
                continue;
            }
            var target = catalog.snapshot().find(f.ref().target());
            if (target.isEmpty()) {
                continue;
            }
            Field display = target.get().model().field(f.ref().display()).orElse(null);
            List<Long> ids = rows.stream().map(r -> (Long) r.get(f.name())).filter(Objects::nonNull).distinct().toList();
            if (display == null || ids.isEmpty()) {
                continue;
            }
            Map<Long, String> m = new HashMap<>();
            QueryBuilder.SqlQuery q = sql.displays(target.get().model(), display.name(), ids);
            jdbc.sql(q.sql()).params(q.params()).query().listOfRows().forEach(r ->
                    m.put(((Number) r.get("id")).longValue(), String.valueOf(codec.fromDb(display, r.get("d")))));
            out.put(f.name(), m);
        }
        return out;
    }

    private List<Field> searchable(Template t, List<Field> readable) {
        List<String> configured = t.view().search();
        return readable.stream()
                .filter(f -> f.type().isString() || f.type() == FieldType.ENUM)
                .filter(f -> configured.isEmpty() ? f.type().isString() : configured.contains(f.name()))
                .toList();
    }

    private Field writableField(Template t, String key, Map<String, String> errors) {
        if (key.equals("row_version") || key.startsWith("$") || key.endsWith("$display") || Model.AUDIT_COLUMNS.contains(key)) {
            return null;
        }
        Field f = t.field(key).orElse(null);
        if (f == null) {
            errors.put(key, "Unknown field");
            return null;
        }
        return f.type() == FieldType.ID ? null : f;
    }

    private void decode(Field f, JsonNode value, Map<String, Object> into, Map<String, String> errors) {
        try {
            into.put(f.name(), codec.fromJson(f, value));
        } catch (ValueCodec.CodecException e) {
            errors.put(f.name(), e.getMessage());
        }
    }

    private Object defaultValue(Field f, TdsPrincipal p) {
        return switch (f.defaultValue()) {
            case DefaultValue.Literal lit -> codec.fromJson(f, lit.value());
            case DefaultValue.Fn fn -> switch (fn.fn()) {
                case NOW -> ValueCodec.nowIso();
                case TODAY -> LocalDate.now(ZoneOffset.UTC).toString();
                case UUID -> UUID.randomUUID().toString();
                case CURRENT_USER -> p.username();
            };
        };
    }

    private void validate(Template t, Map<String, Object> values, WriteOp op, TdsPrincipal p) {
        RecordValidator.Result r = validator.validate(t, values, op, p.username(), p.roles());
        if (!r.ok()) {
            throw new ValidationException(r.fieldErrors(), r.general());
        }
    }

    private Map<String, Object> toDb(Template t, Map<String, Object> canonical) {
        Map<String, Object> db = new LinkedHashMap<>();
        canonical.forEach((k, v) -> t.field(k).filter(f -> f.type() != FieldType.ID).ifPresent(f -> db.put(k, codec.toDb(f, v))));
        return db;
    }

    private <T> T execute(Template t, DbErrorTranslator.Op op, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            ApiException translated = DbErrorTranslator.translate(e, t, op, catalog.snapshot().incomingRefs(t.name()));
            if (translated != null) {
                throw translated;
            }
            throw e;
        }
    }

    private static Set<String> names(List<Field> fields) {
        Set<String> s = new LinkedHashSet<>();
        fields.forEach(f -> s.add(f.name()));
        return s;
    }

    static List<String> list(Object... xs) {
        List<String> out = new ArrayList<>();
        for (Object x : xs) {
            out.add(String.valueOf(x));
        }
        return out;
    }
}
