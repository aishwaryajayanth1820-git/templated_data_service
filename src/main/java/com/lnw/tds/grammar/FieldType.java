package com.lnw.tds.grammar;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Logical column types of {@code tds/v1} (grammar §4.1). JSON value is the lower-case name. */
public enum FieldType {
    ID(List.of()),
    STRING(List.of(Widget.TEXT, Widget.TEXTAREA)),
    TEXT(List.of(Widget.TEXTAREA, Widget.TEXT)),
    INTEGER(List.of(Widget.NUMBER)),
    LONG(List.of(Widget.NUMBER)),
    DECIMAL(List.of(Widget.NUMBER)),
    DOUBLE(List.of(Widget.NUMBER)),
    BOOLEAN(List.of(Widget.SWITCH, Widget.CHECKBOX)),
    DATE(List.of(Widget.DATE)),
    DATETIME(List.of(Widget.DATETIME)),
    TIME(List.of(Widget.TIME)),
    ENUM(List.of(Widget.SELECT, Widget.RADIO)),
    REF(List.of(Widget.LOOKUP)),
    JSON(List.of(Widget.JSON, Widget.TEXTAREA)),
    UUID(List.of(Widget.TEXT));

    private final List<Widget> widgets;

    FieldType(List<Widget> widgets) {
        this.widgets = widgets;
    }

    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<FieldType> fromJson(String value) {
        return Arrays.stream(values()).filter(t -> t.json().equals(value)).findFirst();
    }

    public List<Widget> widgets() {
        return widgets;
    }

    public boolean isString() {
        return this == STRING || this == TEXT;
    }

    public boolean isNumeric() {
        return this == INTEGER || this == LONG || this == DECIMAL || this == DOUBLE;
    }

    public boolean isIntegral() {
        return this == INTEGER || this == LONG;
    }

    public boolean isTemporal() {
        return this == DATE || this == DATETIME || this == TIME;
    }
}
