package io.github.aishwaryajayanth1820.tds.data;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.aishwaryajayanth1820.tds.ddl.SqlDialect;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts values between the API (JSON), canonical Java values used for validation and scripts, and the
 * database (architecture §6.3). Canonical types: String (text, enum, uuid, ISO date/time), Long (integers,
 * refs), BigDecimal (decimal), Double (double), Boolean, and Map/List for json.
 */
@Component
public class ValueCodec {

    public static final DateTimeFormatter INSTANT_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** A value that does not fit its field; message is user-facing. */
    public static class CodecException extends RuntimeException {
        public CodecException(String message) {
            super(message);
        }
    }

    private final SqlDialect dialect;
    private final JsonMapper mapper;

    public ValueCodec(SqlDialect dialect, JsonMapper mapper) {
        this.dialect = dialect;
        this.mapper = mapper;
    }

    /** API JSON → canonical. */
    public Object fromJson(Field f, JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        return switch (f.type()) {
            case STRING, TEXT, ENUM, UUID -> {
                if (v.isString() || v.isNumber() || v.isBoolean()) {
                    yield v.asString();
                }
                throw new CodecException("Must be text");
            }
            case ID, INTEGER, LONG, REF -> {
                if (v.isIntegralNumber()) {
                    yield v.asLong();
                }
                if (v.isNumber() && v.asDouble() == Math.rint(v.asDouble())) {
                    yield (long) v.asDouble();
                }
                if (v.isString() && v.asString().strip().matches("-?\\d{1,18}")) {
                    yield Long.parseLong(v.asString().strip());
                }
                throw new CodecException("Must be a whole number");
            }
            case DECIMAL -> {
                if (v.isNumber()) {
                    yield v.decimalValue();
                }
                yield parseDecimal(v.asString());
            }
            case DOUBLE -> {
                if (v.isNumber()) {
                    yield v.asDouble();
                }
                yield parseDecimal(v.asString()).doubleValue();
            }
            case BOOLEAN -> {
                if (v.isBoolean()) {
                    yield v.asBoolean();
                }
                yield parseBoolean(v.asString());
            }
            case DATE -> date(text(v));
            case DATETIME -> instant(text(v));
            case TIME -> time(text(v));
            case JSON -> mapper.convertValue(v, Object.class);
        };
    }

    /** Query-string value (filters) → canonical. */
    public Object fromString(Field f, String raw) {
        if (raw == null) {
            return null;
        }
        return switch (f.type()) {
            case ID, INTEGER, LONG, REF, DECIMAL, DOUBLE, BOOLEAN -> fromJson(f, mapper.getNodeFactory().stringNode(raw));
            case DATETIME -> instant(raw);
            default -> fromJson(f, mapper.getNodeFactory().stringNode(raw));
        };
    }

    /** Canonical → JDBC parameter. */
    public Object toDb(Field f, Object v) {
        if (v == null) {
            return null;
        }
        boolean sqlite = dialect.isSqlite();
        return switch (f.type()) {
            case BOOLEAN -> sqlite ? (((Boolean) v) ? 1 : 0) : v;
            case DATE -> sqlite ? v : LocalDate.parse((String) v);
            case DATETIME -> sqlite ? v : OffsetDateTime.ofInstant(Instant.parse((String) v), ZoneOffset.UTC);
            case TIME -> sqlite ? v : LocalTime.parse((String) v);
            case JSON -> mapper.writeValueAsString(v);
            case DECIMAL -> sqlite ? ((BigDecimal) v).toPlainString() : v;
            default -> v;
        };
    }

    /** JDBC value → canonical. */
    public Object fromDb(Field f, Object v) {
        if (v == null) {
            return null;
        }
        return switch (f.type()) {
            case ID, INTEGER, LONG, REF -> ((Number) v).longValue();
            case DECIMAL -> v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
            case DOUBLE -> ((Number) v).doubleValue();
            case BOOLEAN -> v instanceof Boolean b ? b : ((Number) v).intValue() != 0;
            case DATE -> v instanceof java.sql.Date d ? d.toLocalDate().toString() : date(v.toString());
            case DATETIME -> instantFromDb(v);
            case TIME -> v instanceof java.sql.Time t ? t.toLocalTime().format(TIME) : time(v.toString());
            case JSON -> readJson(v.toString());
            case UUID -> v instanceof UUID u ? u.toString() : v.toString();
            default -> v.toString();
        };
    }

    /** Audit columns: timestamps → ISO, row_version → Long, *_by → String. */
    public Object auditFromDb(String column, Object v) {
        if (v == null) {
            return null;
        }
        return switch (column) {
            case "created_at", "updated_at" -> instantFromDb(v);
            case "row_version" -> ((Number) v).longValue();
            default -> v.toString();
        };
    }

    public JsonNode toJson(Object canonical) {
        return mapper.valueToTree(canonical);
    }

    public static String nowIso() {
        return INSTANT_MILLIS.format(Instant.now());
    }

    private static String instantFromDb(Object v) {
        return switch (v) {
            case Timestamp t -> INSTANT_MILLIS.format(t.toInstant());
            case OffsetDateTime o -> INSTANT_MILLIS.format(o.toInstant());
            case Instant i -> INSTANT_MILLIS.format(i);
            default -> instant(v.toString());
        };
    }

    /** Any ISO-8601 date-time (with Z or offset; no zone means UTC; a bare date means midnight UTC) → millis Z. */
    static String instant(String s) {
        String t = s.strip();
        try {
            return INSTANT_MILLIS.format(Instant.parse(t));
        } catch (DateTimeParseException ignored) {
            // try the other forms
        }
        try {
            return INSTANT_MILLIS.format(OffsetDateTime.parse(t).toInstant());
        } catch (DateTimeParseException ignored) {
            // try the other forms
        }
        try {
            return INSTANT_MILLIS.format(LocalDateTime.parse(t.replace(' ', 'T')).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // try the other forms
        }
        try {
            return INSTANT_MILLIS.format(LocalDate.parse(t).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            throw new CodecException("Must be a date-time like 2026-10-03T09:42:00Z");
        }
    }

    private static String date(String s) {
        try {
            return LocalDate.parse(s.strip().length() > 10 ? s.strip().substring(0, 10) : s.strip()).toString();
        } catch (DateTimeParseException e) {
            throw new CodecException("Must be a date like 2026-10-03");
        }
    }

    private static String time(String s) {
        try {
            return LocalTime.parse(s.strip()).format(TIME);
        } catch (DateTimeParseException e) {
            throw new CodecException("Must be a time like 08:30:00");
        }
    }

    private static BigDecimal parseDecimal(String s) {
        try {
            return new BigDecimal(s.strip());
        } catch (NumberFormatException | NullPointerException e) {
            throw new CodecException("Must be a number");
        }
    }

    private static boolean parseBoolean(String s) {
        return switch (s == null ? "" : s.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> throw new CodecException("Must be true or false");
        };
    }

    private static String text(JsonNode v) {
        if (!v.isString()) {
            throw new CodecException("Must be text");
        }
        return v.asString();
    }

    private Object readJson(String text) {
        try {
            return mapper.readValue(text, Object.class);
        } catch (JacksonException e) {
            return text;
        }
    }
}
