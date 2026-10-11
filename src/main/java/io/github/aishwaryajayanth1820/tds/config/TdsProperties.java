package io.github.aishwaryajayanth1820.tds.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Service settings under {@code tds.*} (architecture §4). */
@ConfigurationProperties("tds")
public record TdsProperties(Paths paths, Seed seed, Scripts scripts, Security security) {

    public record Paths(Path templates, Path scripts, Path output) {}

    public record Seed(boolean importOnStart, boolean autoPublish, boolean sampleData) {}

    public record Scripts(boolean watch, int maxConcurrent, int defaultTimeoutMs) {}

    public record Security(String bootstrapAdmin) {}
}
