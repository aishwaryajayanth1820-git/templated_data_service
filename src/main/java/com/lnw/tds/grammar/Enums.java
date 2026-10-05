package com.lnw.tds.grammar;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Small closed vocabularies of {@code tds/v1}. JSON values are the lower-case names unless noted. */
public final class Enums {

    private Enums() {}

    static <E extends Enum<E>> Optional<E> lower(Class<E> type, String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.name().replace("_", "").equalsIgnoreCase(value.replace("_", "")))
                .findFirst();
    }

    static String json(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }
}
