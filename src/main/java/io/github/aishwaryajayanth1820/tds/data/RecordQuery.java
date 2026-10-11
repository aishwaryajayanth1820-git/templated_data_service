package io.github.aishwaryajayanth1820.tds.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.MultiValueMap;

import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.SortDir;
import io.github.aishwaryajayanth1820.tds.grammar.Model.SortSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;
import io.github.aishwaryajayanth1820.tds.web.ApiException;

/**
 * List query (architecture §8.2, ADR-0011): {@code page}, {@code size}, {@code sort=field,dir} (repeatable),
 * {@code q} and filters {@code f.<field>[.<op>]=value}. Only readable fields may be filtered, sorted or searched.
 */
public record RecordQuery(int page, int size, List<SortSpec> sort, String q, List<Filter> filters) {

    public static final int MAX_SIZE = 500;

    public enum Op { EQ, NE, GT, GTE, LT, LTE, IN, LIKE, NULL }

    public record Filter(Field field, Op op, String raw) {}

    static RecordQuery parse(MultiValueMap<String, String> params, Template t, Set<String> readable) {
        int page = intParam(params, "page", 0);
        int size = intParam(params, "size", t.view().pageSize());
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw ApiException.badRequest("page must be ≥ 0 and size between 1 and " + MAX_SIZE);
        }
        List<SortSpec> sort = new ArrayList<>();
        for (String s : params.getOrDefault("sort", List.of())) {
            String[] parts = s.split(",");
            String field = parts[0].strip();
            if (!sortable(t, readable, field)) {
                throw ApiException.badRequest("Cannot sort by " + field);
            }
            boolean desc = parts.length > 1 && parts[1].strip().equalsIgnoreCase("desc");
            sort.add(new SortSpec(field, desc ? SortDir.DESC : SortDir.ASC));
        }
        if (sort.isEmpty()) {
            t.view().defaultSort().stream().filter(s -> sortable(t, readable, s.field())).forEach(sort::add);
        }
        List<Filter> filters = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            if (!e.getKey().startsWith("f.")) {
                continue;
            }
            String[] parts = e.getKey().substring(2).split("\\.");
            Field f = t.field(parts[0]).filter(x -> readable.contains(x.name()))
                    .orElseThrow(() -> ApiException.badRequest("Cannot filter by " + parts[0]));
            Op op = parts.length > 1 ? op(parts[1]) : Op.EQ;
            for (String raw : e.getValue()) {
                filters.add(new Filter(f, op, raw));
            }
        }
        String q = params.getFirst("q");
        return new RecordQuery(page, size, sort, q == null || q.isBlank() ? null : q.strip(), filters);
    }

    private static boolean sortable(Template t, Set<String> readable, String field) {
        return readable.contains(field) || (t.referenceableNames().contains(field) && t.field(field).isEmpty());
    }

    private static Op op(String s) {
        try {
            return Op.valueOf(s.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown filter operator " + s);
        }
    }

    private static int intParam(MultiValueMap<String, String> params, String name, int dflt) {
        String v = params.getFirst(name);
        if (v == null || v.isBlank()) {
            return dflt;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(name + " must be a number");
        }
    }
}
