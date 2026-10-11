package io.github.aishwaryajayanth1820.tds.admin;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.aishwaryajayanth1820.tds.script.ScriptRegistry;
import io.github.aishwaryajayanth1820.tds.web.ApiException;

/** Script files admin (architecture §8.3). Paths contain '/', so they travel as a query parameter. */
@RestController
@RequestMapping("/api/admin/scripts")
class ScriptAdminController {

    record ScriptInfo(String path, ScriptRegistry.Status status, String error, Set<String> functions, Instant loadedAt) {}

    record ScriptContent(String path, ScriptRegistry.Status status, String error, Set<String> functions, String content) {}

    record WriteRequest(String content) {}

    private final ScriptRegistry registry;

    ScriptAdminController(ScriptRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    List<ScriptInfo> list() {
        return registry.list().stream().map(f -> new ScriptInfo(f.path(), f.status(), f.error(), f.functions(), f.loadedAt())).toList();
    }

    @GetMapping("/content")
    ScriptContent read(@RequestParam String path) {
        ScriptRegistry.ScriptFile f = registry.get(path).orElseThrow(() -> ApiException.notFound("Script " + path));
        return new ScriptContent(f.path(), f.status(), f.error(), f.functions(), f.content());
    }

    @PutMapping("/content")
    ScriptContent write(@RequestParam String path, @RequestBody WriteRequest body) {
        ScriptRegistry.ScriptFile f = registry.write(path, body.content() == null ? "" : body.content());
        return new ScriptContent(f.path(), f.status(), f.error(), f.functions(), f.content());
    }
}
