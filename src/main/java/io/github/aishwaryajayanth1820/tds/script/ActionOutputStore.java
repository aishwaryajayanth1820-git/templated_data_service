package io.github.aishwaryajayanth1820.tds.script;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.github.aishwaryajayanth1820.tds.config.TdsProperties;
import io.github.aishwaryajayanth1820.tds.web.ApiException;

/** The only place scripts may write files: {@code <output>/<template>/<name>} (ADR-0006). */
@Component
class ActionOutputStore {

    static final Pattern FILE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");
    static final int MAX_BYTES = 1_000_000;

    private final Path root;

    ActionOutputStore(TdsProperties properties) {
        this.root = properties.paths().output().toAbsolutePath().normalize();
    }

    /** Writes a text file and returns its name. Throws {@link IllegalArgumentException} with a script-facing message. */
    String writeText(String template, String name, String text) {
        if (name == null || !FILE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("files.writeText: invalid file name '" + name + "' (letters, digits, . _ - only)");
        }
        byte[] bytes = String.valueOf(text).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("files.writeText: content larger than 1 MB");
        }
        Path file = resolve(template, name);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    Path read(String template, String name) {
        if (name == null || !FILE_NAME.matcher(name).matches()) {
            throw ApiException.notFound("File " + name);
        }
        Path file = resolve(template, name);
        if (!Files.isRegularFile(file)) {
            throw ApiException.notFound("File " + name);
        }
        return file;
    }

    private Path resolve(String template, String name) {
        Path file = root.resolve(template).resolve(name).normalize();
        if (!file.startsWith(root.resolve(template))) {
            throw new IllegalArgumentException("files.writeText: path outside the output directory");
        }
        return file;
    }
}
