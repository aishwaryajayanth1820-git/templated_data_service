package io.github.aishwaryajayanth1820.tds.data;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;

import io.github.aishwaryajayanth1820.tds.grammar.Model.Rule;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;
import io.github.aishwaryajayanth1820.tds.web.ApiException;
import io.github.aishwaryajayanth1820.tds.web.ErrorCode;
import io.github.aishwaryajayanth1820.tds.web.ValidationException;

/** Turns database constraint failures (SQLite messages, PostgreSQL SQLSTATEs) into user-facing API errors. */
final class DbErrorTranslator {

    enum Op { WRITE, DELETE }

    private static final Pattern SQLITE_COLUMNS = Pattern.compile("constraint failed: ([\\w.\", ]+)");
    private static final Pattern PG_KEY = Pattern.compile("Key \\(([^)]+)\\)=");
    private static final Pattern PG_COLUMN = Pattern.compile("column \"(\\w+)\"");
    private static final Pattern PG_CONSTRAINT = Pattern.compile("constraint \"(\\w+)\"");

    private DbErrorTranslator() {}

    /** Returns the translated exception, or {@code null} when the failure is not a constraint violation. */
    static ApiException translate(DataAccessException e, Template t, Op op, List<String> incomingRefs) {
        SQLException sql = sqlException(e);
        String msg = sql != null && sql.getMessage() != null ? sql.getMessage() : String.valueOf(e.getMessage());
        String state = sql != null ? sql.getSQLState() : null;

        if (msg.contains("UNIQUE constraint failed") || "23505".equals(state)) {
            Map<String, String> errors = new LinkedHashMap<>();
            columns(msg).forEach(c -> errors.put(c, "Already used by another row"));
            return new ApiException(ErrorCode.CONFLICT_UNIQUE, "Duplicate value",
                    errors.isEmpty() ? "A row with the same unique value already exists" : "Values must be unique: " + String.join(", ", errors.keySet()),
                    Map.of("errors", errors, "general", List.of()));
        }
        if (msg.contains("FOREIGN KEY constraint failed") || "23503".equals(state)) {
            if (op == Op.DELETE) {
                return new ApiException(ErrorCode.CONFLICT_REFERENCE, "Row in use",
                        "Other rows still reference this row" + (incomingRefs.isEmpty() ? "" : " (" + String.join(", ", incomingRefs) + ")"));
            }
            return new ValidationException(Map.of(), List.of("A referenced row does not exist"));
        }
        if (msg.contains("NOT NULL constraint failed") || "23502".equals(state)) {
            Map<String, String> errors = new LinkedHashMap<>();
            columns(msg).forEach(c -> errors.put(c, "Required"));
            return new ValidationException(errors, errors.isEmpty() ? List.of("A required value is missing") : List.of());
        }
        if (msg.contains("CHECK constraint failed") || "23514".equals(state)) {
            for (Rule r : t.rules()) {
                if (msg.contains("ck_" + t.name() + "_" + r.id())) {
                    Map<String, String> errors = new LinkedHashMap<>();
                    String m = r.message() != null ? r.message() : "Rule " + r.id() + " failed";
                    r.targets().forEach(f -> errors.put(f, m));
                    return new ValidationException(errors, errors.isEmpty() ? List.of(m) : List.of());
                }
            }
            Map<String, String> errors = new LinkedHashMap<>();
            t.fields().stream().filter(f -> msg.contains("\"" + f.name() + "\"") || msg.contains("(" + f.name() + ")"))
                    .forEach(f -> errors.put(f.name(), "Value is not allowed"));
            return new ValidationException(errors, errors.isEmpty() ? List.of("A value is not allowed by the table's rules") : List.of());
        }
        return null;
    }

    private static List<String> columns(String msg) {
        List<String> out = new ArrayList<>();
        Matcher pg = PG_KEY.matcher(msg);
        if (pg.find()) {
            for (String c : pg.group(1).split(",")) {
                out.add(c.strip().replace("\"", ""));
            }
            return out;
        }
        Matcher col = PG_COLUMN.matcher(msg);
        if (col.find() && !msg.contains("constraint failed")) {
            out.add(col.group(1));
            return out;
        }
        Matcher m = SQLITE_COLUMNS.matcher(msg);
        if (m.find()) {
            for (String c : m.group(1).split(",")) {
                String s = c.strip().replace("\"", "");
                int dot = s.lastIndexOf('.');
                out.add(dot >= 0 ? s.substring(dot + 1).replaceAll("\\W.*$", "") : s);
            }
        }
        Matcher cons = PG_CONSTRAINT.matcher(msg);
        if (out.isEmpty() && cons.find()) {
            out.add(cons.group(1));
        }
        return out;
    }

    private static SQLException sqlException(Throwable e) {
        Throwable t = e;
        SQLException last = null;
        while (t != null) {
            if (t instanceof SQLException s) {
                last = s;
            }
            t = t.getCause() == t ? null : t.getCause();
        }
        return last;
    }
}
