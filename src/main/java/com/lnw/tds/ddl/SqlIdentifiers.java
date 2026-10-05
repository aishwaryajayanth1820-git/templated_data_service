package com.lnw.tds.ddl;

import java.util.regex.Pattern;

/** The only way table and column names enter SQL: validated names, double-quoted (safe for reserved words). */
public final class SqlIdentifiers {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private SqlIdentifiers() {}

    public static String quote(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Illegal SQL identifier: " + name);
        }
        return "\"" + name + "\"";
    }
}
