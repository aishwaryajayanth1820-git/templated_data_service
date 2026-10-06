package io.github.aishwaryajayanth1820.tds;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.aishwaryajayanth1820.tds.security.TdsPrincipal;

/**
 * Base for {@code *IT} tests: a real server on a random port, a throw-away SQLite file, the seed templates
 * auto-published with their sample rows, and a private copy of {@code scripts/}. All subclasses share one
 * context and database, so tests create their own data (unique names) instead of relying on a clean slate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "TDS_ADMIN_PASSWORD=" + IntegrationTestBase.ADMIN_PASSWORD, "tds.scripts.watch=false",
                "tds.seed.auto-publish=true", "tds.seed.sample-data=true" })
public abstract class IntegrationTestBase {

    public static final String ADMIN_PASSWORD = "bootstrap-secret-1";

    public static final TdsPrincipal ADMIN = new TdsPrincipal("admin", "Administrator", Set.of("admin"), false);
    public static final TdsPrincipal VIEWER = new TdsPrincipal("vera", "Vera Viewer", Set.of(), false);

    private static final Path WORK = createWorkDir();

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + WORK.resolve("tds.db"));
        registry.add("tds.paths.scripts", () -> WORK.resolve("scripts").toString());
        registry.add("tds.paths.output", () -> WORK.resolve("action-output").toString());
    }

    protected static Path workDir() {
        return WORK;
    }

    private static Path createWorkDir() {
        try {
            Path target = Path.of("target");
            Files.createDirectories(target);
            Path dir = Files.createTempDirectory(target, "it-");
            copyTree(Path.of("scripts"), dir.resolve("scripts"));
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
