package io.github.aishwaryajayanth1820.tds.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.github.aishwaryajayanth1820.tds.config.TdsProperties;
import io.github.aishwaryajayanth1820.tds.web.ApiException;

/**
 * Startup: loads the catalog, imports {@code templates/*.json} that are not in the database yet as drafts,
 * and (when {@code tds.seed.auto-publish}) publishes never-published drafts in reference order.
 * Existing drafts are never overwritten; use Studio import for that.
 */
@Component
@Order(30)
class TemplateSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TemplateSeeder.class);
    static final String SYSTEM_USER = "system";

    private final CatalogService catalog;
    private final DraftService drafts;
    private final PublishService publisher;
    private final TemplateRepository repository;
    private final TdsProperties properties;

    TemplateSeeder(CatalogService catalog, DraftService drafts, PublishService publisher, TemplateRepository repository,
                   TdsProperties properties) {
        this.catalog = catalog;
        this.drafts = drafts;
        this.publisher = publisher;
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        catalog.reload();
        if (properties.seed().importOnStart()) {
            importFiles(properties.paths().templates());
        }
        if (properties.seed().autoPublish()) {
            autoPublish();
        }
    }

    void importFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            log.info("No template directory at {}", dir.toAbsolutePath());
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                String name = drafts.readObject(json).path("name").asString("");
                if (repository.find(name).isPresent()) {
                    continue;
                }
                try {
                    drafts.create(json, SYSTEM_USER);
                    log.info("Imported template draft {} from {}", name, file.getFileName());
                } catch (ApiException e) {
                    log.warn("Skipped {}: {}", file.getFileName(), e.getMessage());
                }
            }
        }
    }

    /** Publishes unpublished drafts, retrying until no further progress (ref targets must go first). */
    void autoPublish() {
        List<String> pending = new ArrayList<>(repository.findAll().stream()
                .filter(r -> r.publishedVersion() == null).map(TemplateRepository.Row::name).toList());
        boolean progress = true;
        List<String> lastErrors = new ArrayList<>();
        while (!pending.isEmpty() && progress) {
            progress = false;
            lastErrors.clear();
            for (String name : List.copyOf(pending)) {
                try {
                    int v = publisher.publish(name, new PublishService.PublishRequest(null, name), SYSTEM_USER);
                    log.info("Auto-published {} v{}", name, v);
                    pending.remove(name);
                    progress = true;
                } catch (ApiException e) {
                    lastErrors.add(name + ": " + e.getMessage());
                }
            }
        }
        lastErrors.forEach(m -> log.warn("Not auto-published: {}", m));
    }
}
