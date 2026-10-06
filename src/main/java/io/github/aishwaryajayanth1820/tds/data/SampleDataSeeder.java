package io.github.aishwaryajayanth1820.tds.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.github.aishwaryajayanth1820.tds.catalog.CatalogService;
import io.github.aishwaryajayanth1820.tds.config.TdsProperties;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Development aid: fills empty published tables from {@code templates/sample-data/<table>.json}, inserted
 * directly (no app validation) as an external process would for VIEW / DATA_SOURCE tables.
 */
@Component
@Order(40)
@ConditionalOnProperty(name = "tds.seed.sample-data", havingValue = "true")
class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final CatalogService catalog;
    private final RecordService records;
    private final ValueCodec codec;
    private final JsonMapper mapper;
    private final TdsProperties properties;

    SampleDataSeeder(CatalogService catalog, RecordService records, ValueCodec codec, JsonMapper mapper, TdsProperties properties) {
        this.catalog = catalog;
        this.records = records;
        this.codec = codec;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path dir = properties.paths().templates().resolve("sample-data");
        // Ref targets first: tables with no outgoing refs, then the rest.
        var ordered = catalog.all().stream().map(CatalogService.Published::model)
                .sorted(java.util.Comparator.comparingLong(t -> t.fields().stream().filter(f -> f.ref() != null).count()))
                .toList();
        for (Template t : ordered) {
            Path file = dir.resolve(t.name() + ".json");
            if (!Files.isRegularFile(file) || records.count(t) > 0) {
                continue;
            }
            JsonNode rows = mapper.readTree(file.toFile());
            int n = 0;
            for (JsonNode row : rows.values()) {
                Map<String, Object> values = new LinkedHashMap<>();
                if (row.has("id")) {
                    values.put("id", row.get("id").asLong());
                }
                for (Field f : t.fields()) {
                    if (row.has(f.name()) && f.type() != io.github.aishwaryajayanth1820.tds.grammar.FieldType.ID) {
                        values.put(f.name(), codec.fromJson(f, row.get(f.name())));
                    }
                }
                try {
                    records.insertRaw(t, values, "sample-data");
                    n++;
                } catch (RuntimeException e) {
                    log.warn("Sample row for {} skipped: {}", t.name(), e.getMessage());
                }
            }
            log.info("Inserted {} sample row(s) into {}", n, t.name());
        }
    }
}
