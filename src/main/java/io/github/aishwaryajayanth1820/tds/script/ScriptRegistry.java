package io.github.aishwaryajayanth1820.tds.script;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.aishwaryajayanth1820.tds.config.TdsProperties;
import io.github.aishwaryajayanth1820.tds.grammar.ValidationContext;
import io.github.aishwaryajayanth1820.tds.web.ApiException;
import io.github.aishwaryajayanth1820.tds.web.ValidationException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Action scripts under {@code tds.paths.scripts} (ADR-0006): loaded at startup, reloaded on change. A file that
 * fails to load keeps its last good version active and reports {@code ERROR}.
 */
@Component
public class ScriptRegistry implements ValidationContext.ScriptCatalog {

    private static final Logger log = LoggerFactory.getLogger(ScriptRegistry.class);
    static final Pattern PATH = Pattern.compile("^(?!/)(?!.*\\.\\.)[A-Za-z0-9_\\-/]+\\.js$");

    public enum Status { OK, ERROR }

    /** {@code source}/{@code functions} are the last good version; {@code content} is what is on disk. */
    public record ScriptFile(String path, String content, String sha256, Source source, Set<String> functions, Status status,
                             String error, Instant loadedAt) {}

    private final Path root;
    private final Engine engine;
    private final Map<String, ScriptFile> files = new ConcurrentHashMap<>();

    public ScriptRegistry(TdsProperties properties) {
        this.root = properties.paths().scripts().toAbsolutePath().normalize();
        this.engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
    }

    public Engine engine() {
        return engine;
    }

    public Path root() {
        return root;
    }

    @PostConstruct
    public void loadAll() {
        try {
            Files.createDirectories(root);
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".js") && Files.isRegularFile(p)).forEach(this::reload);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read scripts from " + root, e);
        }
        log.info("Loaded {} script(s) from {}", files.size(), root);
    }

    @PreDestroy
    void close() {
        engine.close();
    }

    public List<ScriptFile> list() {
        return new ArrayList<>(new TreeMap<>(files).values());
    }

    public Optional<ScriptFile> get(String path) {
        return Optional.ofNullable(files.get(path));
    }

    @Override
    public boolean exists(String scriptPath) {
        return files.containsKey(scriptPath);
    }

    @Override
    public boolean hasFunction(String scriptPath, String function) {
        ScriptFile f = files.get(scriptPath);
        return f != null && f.functions().contains(function);
    }

    /** (Re)loads one file from disk. */
    public void reload(Path file) {
        String rel = relative(file);
        if (!Files.isRegularFile(file)) {
            if (files.remove(rel) != null) {
                log.info("Script removed: {}", rel);
            }
            return;
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Cannot read script {}: {}", rel, e.getMessage());
            return;
        }
        ScriptFile previous = files.get(rel);
        if (previous != null && previous.content().equals(content) && previous.status() == Status.OK) {
            return;
        }
        try {
            Source source = Source.newBuilder("js", content, rel).build();
            Set<String> functions = discover(source);
            files.put(rel, new ScriptFile(rel, content, sha256(content), source, functions, Status.OK, null, Instant.now()));
            log.info("Script loaded: {} {}", rel, functions);
        } catch (IOException | PolyglotException e) {
            String error = e.getMessage();
            files.put(rel, new ScriptFile(rel, content, sha256(content), previous != null ? previous.source() : null,
                    previous != null ? previous.functions() : Set.of(), Status.ERROR, error, Instant.now()));
            log.warn("Script {} failed to load ({}); {}", rel, error,
                    previous != null ? "keeping the last good version" : "no previous version");
        }
    }

    /** Writes a script atomically (admin / Studio) and loads it. */
    public ScriptFile write(String path, String content) {
        Path target = resolve(path);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".tds-", ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write script " + path, e);
        }
        reload(target);
        return files.get(path);
    }

    Path resolve(String path) {
        if (path == null || !PATH.matcher(path).matches()) {
            throw ValidationException.field("path", "Use a relative path like alerts/create_ticket.js");
        }
        Path p = root.resolve(path).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.badRequest("Path outside the scripts directory");
        }
        return p;
    }

    String relative(Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private Set<String> discover(Source source) {
        try (Context c = Context.newBuilder("js").engine(engine).allowHostAccess(HostAccess.NONE).allowIO(IOAccess.NONE)
                .allowCreateThread(false).allowHostClassLookup(n -> false).build()) {
            c.eval(source);
            Value bindings = c.getBindings("js");
            Set<String> out = new TreeSet<>();
            for (String k : bindings.getMemberKeys()) {
                if (bindings.getMember(k).canExecute()) {
                    out.add(k);
                }
            }
            return out;
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
