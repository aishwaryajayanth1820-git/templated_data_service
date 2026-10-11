package io.github.aishwaryajayanth1820.tds.jdbc;

/** Database engines supported by TDS (ADR-0004). */
public enum DbVendor {
    SQLITE("strftime('%Y-%m-%dT%H:%M:%fZ','now')"),
    POSTGRESQL("now()");

    private final String nowSql;

    DbVendor(String nowSql) {
        this.nowSql = nowSql;
    }

    /** SQL expression for the current UTC timestamp, in the vendor's storage format. */
    public String nowSql() {
        return nowSql;
    }

    static DbVendor fromProductName(String productName) {
        return switch (productName.toLowerCase(java.util.Locale.ROOT)) {
            case "sqlite" -> SQLITE;
            case "postgresql" -> POSTGRESQL;
            default -> throw new IllegalStateException("Unsupported database: " + productName);
        };
    }
}
