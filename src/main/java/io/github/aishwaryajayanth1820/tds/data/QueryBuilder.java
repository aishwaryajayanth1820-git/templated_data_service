package io.github.aishwaryajayanth1820.tds.data;

import static io.github.aishwaryajayanth1820.tds.ddl.SqlIdentifiers.quote;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.aishwaryajayanth1820.tds.ddl.SqlDialect;
import io.github.aishwaryajayanth1820.tds.grammar.FieldType;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.SortDir;
import io.github.aishwaryajayanth1820.tds.grammar.Model.SortSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;
import io.github.aishwaryajayanth1820.tds.web.ApiException;

/** Parameterised SQL for the generic record API. Identifiers come only from the template (catalog). */
final class QueryBuilder {

    record SqlQuery(String sql, Map<String, Object> params) {}

    private final SqlDialect dialect;
    private final ValueCodec codec;

    QueryBuilder(SqlDialect dialect, ValueCodec codec) {
        this.dialect = dialect;
        this.codec = codec;
    }

    SqlQuery select(Template t, RecordQuery q, List<Field> searchable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = where(t, q, searchable, params);
        StringBuilder order = new StringBuilder();
        for (SortSpec s : q.sort()) {
            String expr = quote(s.field());
            Field f = t.field(s.field()).orElse(null);
            if (f != null && f.type() == FieldType.REF && f.ref() != null) {
                expr = "(SELECT r." + quote(f.ref().display()) + " FROM " + quote(f.ref().target()) + " r WHERE r.\"id\" = "
                        + quote(t.name()) + "." + quote(f.name()) + ")";
            }
            order.append(expr).append(s.dir() == SortDir.DESC ? " DESC" : " ASC").append(", ");
        }
        order.append(quote(t.idField().name())).append(" ASC");
        params.put("_limit", q.size());
        params.put("_offset", (long) q.page() * q.size());
        return new SqlQuery("SELECT * FROM " + quote(t.name()) + where + " ORDER BY " + order + " LIMIT :_limit OFFSET :_offset",
                params);
    }

    SqlQuery count(Template t, RecordQuery q, List<Field> searchable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = where(t, q, searchable, params);
        return new SqlQuery("SELECT COUNT(*) FROM " + quote(t.name()) + where, params);
    }

    SqlQuery byId(Template t, long id) {
        return new SqlQuery("SELECT * FROM " + quote(t.name()) + " WHERE " + quote(t.idField().name()) + " = :id", Map.of("id", id));
    }

    SqlQuery byIds(Template t, List<Long> ids) {
        return new SqlQuery("SELECT * FROM " + quote(t.name()) + " WHERE " + quote(t.idField().name()) + " IN (:ids)",
                Map.of("ids", ids));
    }

    SqlQuery displays(Template target, String displayField, List<Long> ids) {
        return new SqlQuery("SELECT \"id\", " + quote(displayField) + " AS d FROM " + quote(target.name()) + " WHERE \"id\" IN (:ids)",
                Map.of("ids", ids));
    }

    SqlQuery options(Template target, String displayField, String q, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = "";
        if (q != null && !q.isBlank()) {
            where = " WHERE " + quote(displayField) + " " + dialect.likeOperator() + " :q ESCAPE '\\'";
            params.put("q", "%" + escapeLike(q) + "%");
        }
        params.put("_limit", limit);
        return new SqlQuery("SELECT \"id\", " + quote(displayField) + " AS d FROM " + quote(target.name()) + where
                + " ORDER BY " + quote(displayField) + " LIMIT :_limit", params);
    }

    /** {@code values}: column → JDBC value. Returns the new id. */
    SqlQuery insert(Template t, Map<String, Object> values) {
        List<String> cols = new ArrayList<>(values.keySet());
        String sql = "INSERT INTO " + quote(t.name()) + " (" + cols.stream().map(c -> quote(c)).collect(Collectors.joining(", "))
                + ") VALUES (" + cols.stream().map(c -> placeholder(t, c, "v_" + c)).collect(Collectors.joining(", ")) + ") RETURNING "
                + quote(t.idField().name());
        Map<String, Object> params = new LinkedHashMap<>();
        values.forEach((k, v) -> params.put("v_" + k, v));
        return new SqlQuery(sql, params);
    }

    /** Optimistic lock: when the template has row_version, only updates the expected version. */
    SqlQuery update(Template t, long id, Long expectedVersion, Map<String, Object> values, String username) {
        List<String> sets = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        values.forEach((k, v) -> {
            sets.add(quote(k) + " = " + placeholder(t, k, "v_" + k));
            params.put("v_" + k, v);
        });
        if (t.hasAuditColumns()) {
            sets.add("\"updated_at\" = " + dialect.nowExpression());
            sets.add("\"updated_by\" = :_user");
            params.put("_user", username);
        }
        String where = " WHERE " + quote(t.idField().name()) + " = :_id";
        params.put("_id", id);
        if (t.hasRowVersion()) {
            sets.add("\"row_version\" = \"row_version\" + 1");
            where += " AND \"row_version\" = :_version";
            params.put("_version", expectedVersion);
        }
        return new SqlQuery("UPDATE " + quote(t.name()) + " SET " + String.join(", ", sets) + where, params);
    }

    SqlQuery delete(Template t, long id) {
        return new SqlQuery("DELETE FROM " + quote(t.name()) + " WHERE " + quote(t.idField().name()) + " = :id", Map.of("id", id));
    }

    private String where(Template t, RecordQuery q, List<Field> searchable, Map<String, Object> params) {
        List<String> conds = new ArrayList<>();
        int i = 0;
        for (RecordQuery.Filter f : q.filters()) {
            conds.add(condition(t, f, "f" + i++, params));
        }
        if (q.q() != null && !searchable.isEmpty()) {
            params.put("_q", "%" + escapeLike(q.q()) + "%");
            conds.add("(" + searchable.stream().map(f -> quote(f.name()) + " " + dialect.likeOperator() + " :_q ESCAPE '\\'")
                    .collect(Collectors.joining(" OR ")) + ")");
        }
        return conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
    }

    private String condition(Template t, RecordQuery.Filter f, String p, Map<String, Object> params) {
        Field field = f.field();
        String col = quote(field.name());
        try {
            return switch (f.op()) {
                case NULL -> col + (Boolean.parseBoolean(f.raw()) ? " IS NULL" : " IS NOT NULL");
                case LIKE -> {
                    params.put(p, "%" + escapeLike(f.raw()) + "%");
                    yield col + " " + dialect.likeOperator() + " :" + p + " ESCAPE '\\'";
                }
                case IN -> {
                    List<Object> values = Arrays.stream(f.raw().split(",")).map(String::strip).filter(s -> !s.isEmpty())
                            .map(s -> codec.toDb(field, codec.fromString(field, s))).toList();
                    if (values.isEmpty()) {
                        yield "1 = 0";
                    }
                    params.put(p, values);
                    yield col + " IN (:" + p + ")";
                }
                default -> {
                    params.put(p, codec.toDb(field, codec.fromString(field, f.raw())));
                    String op = switch (f.op()) {
                        case EQ -> "=";
                        case NE -> "<>";
                        case GT -> ">";
                        case GTE -> ">=";
                        case LT -> "<";
                        case LTE -> "<=";
                        default -> throw new IllegalStateException();
                    };
                    yield col + " " + op + " " + placeholder(t, field.name(), p);
                }
            };
        } catch (ValueCodec.CodecException e) {
            throw ApiException.badRequest("Filter " + field.name() + ": " + e.getMessage());
        }
    }

    /** PostgreSQL needs explicit casts for json / uuid parameters bound as text. */
    private String placeholder(Template t, String column, String param) {
        if (!dialect.isSqlite()) {
            FieldType type = t.field(column).map(Field::type).orElse(null);
            if (type == FieldType.JSON) {
                return "CAST(:" + param + " AS jsonb)";
            }
            if (type == FieldType.UUID) {
                return "CAST(:" + param + " AS uuid)";
            }
        }
        return ":" + param;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
