package io.github.aishwaryajayanth1820.tds.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

/** Reads timestamps stored as ISO-8601 text (SQLite) or TIMESTAMPTZ (PostgreSQL). */
public final class JdbcTime {

    private JdbcTime() {}

    public static Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant i -> i;
            case Timestamp t -> t.toInstant();
            case OffsetDateTime o -> o.toInstant();
            case String s -> Instant.parse(s);
            default -> throw new IllegalArgumentException("Not a timestamp: " + value.getClass().getName());
        };
    }
}
