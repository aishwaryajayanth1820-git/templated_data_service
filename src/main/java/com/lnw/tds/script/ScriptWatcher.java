package com.lnw.tds.script;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.lnw.tds.config.TdsProperties;

/** Hot-reloads scripts when files under the scripts directory change (ADR-0006). Events are debounced. */
@Component
class ScriptWatcher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ScriptWatcher.class);
    private static final long DEBOUNCE_MS = 300;

    private final ScriptRegistry registry;
    private final boolean enabled;
    private final Map<WatchKey, Path> dirs = new HashMap<>();
    private volatile WatchService watcher;
    private volatile Thread thread;

    ScriptWatcher(ScriptRegistry registry, TdsProperties properties) {
        this.registry = registry;
        this.enabled = properties.scripts().watch();
    }

    @Override
    public void start() {
        if (!enabled) {
            return;
        }
        try {
            watcher = registry.root().getFileSystem().newWatchService();
            registerTree(registry.root());
        } catch (IOException e) {
            log.warn("Script hot reload disabled: {}", e.getMessage());
            return;
        }
        thread = Thread.ofPlatform().daemon().name("tds-script-watcher").start(this::loop);
        log.info("Watching {} for script changes", registry.root());
    }

    @Override
    public void stop() {
        try {
            if (watcher != null) {
                watcher.close();
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        thread = null;
    }

    @Override
    public boolean isRunning() {
        return thread != null;
    }

    private void loop() {
        try {
            while (true) {
                WatchKey key = watcher.take();
                Set<Path> changed = new LinkedHashSet<>();
                collect(key, changed);
                WatchKey more;
                while ((more = watcher.poll(DEBOUNCE_MS, TimeUnit.MILLISECONDS)) != null) {
                    collect(more, changed);
                }
                for (Path p : changed) {
                    if (Files.isDirectory(p)) {
                        registerTree(p);
                        try (Stream<Path> s = Files.walk(p)) {
                            s.filter(x -> x.toString().endsWith(".js")).forEach(registry::reload);
                        }
                    } else if (p.toString().endsWith(".js")) {
                        registry.reload(p);
                    }
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.warn("Script watcher stopped: {}", e.getMessage());
        }
    }

    private void collect(WatchKey key, Set<Path> changed) {
        Path dir = dirs.get(key);
        for (WatchEvent<?> event : key.pollEvents()) {
            if (dir != null && event.context() instanceof Path name) {
                changed.add(dir.resolve(name));
            }
        }
        key.reset();
    }

    private void registerTree(Path start) throws IOException {
        try (Stream<Path> walk = Files.walk(start)) {
            for (Path d : walk.filter(Files::isDirectory).toList()) {
                dirs.put(d.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE), d);
            }
        }
    }
}
