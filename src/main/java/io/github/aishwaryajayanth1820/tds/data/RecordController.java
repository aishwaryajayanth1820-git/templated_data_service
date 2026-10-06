package io.github.aishwaryajayanth1820.tds.data;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.aishwaryajayanth1820.tds.security.CurrentPrincipal;

import tools.jackson.databind.node.ObjectNode;

/** REST API for every published table (architecture §8.2). */
@RestController
@RequestMapping("/api/data/{table}")
class RecordController {

    private final RecordService records;

    RecordController(RecordService records) {
        this.records = records;
    }

    @GetMapping
    RecordService.Page list(@PathVariable String table, @RequestParam MultiValueMap<String, String> params) {
        return records.list(table, params, CurrentPrincipal.get());
    }

    @GetMapping("/{id:\\d+}")
    ObjectNode get(@PathVariable String table, @PathVariable long id) {
        return records.get(table, id, CurrentPrincipal.get());
    }

    @PostMapping
    ResponseEntity<ObjectNode> create(@PathVariable String table, @RequestBody ObjectNode body) {
        ObjectNode created = records.create(table, body, CurrentPrincipal.get());
        return ResponseEntity.created(URI.create("/api/data/" + table + "/" + created.path("id").asLong())).body(created);
    }

    @PutMapping("/{id:\\d+}")
    ObjectNode replace(@PathVariable String table, @PathVariable long id, @RequestBody ObjectNode body) {
        return records.update(table, id, body, false, CurrentPrincipal.get());
    }

    @PatchMapping("/{id:\\d+}")
    ObjectNode patch(@PathVariable String table, @PathVariable long id, @RequestBody ObjectNode body) {
        return records.update(table, id, body, true, CurrentPrincipal.get());
    }

    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<Void> delete(@PathVariable String table, @PathVariable long id) {
        records.delete(table, id, CurrentPrincipal.get());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fields/{field}/options")
    List<RecordService.Option> options(@PathVariable String table, @PathVariable String field,
                                       @RequestParam(defaultValue = "") String q) {
        return records.options(table, field, q, CurrentPrincipal.get());
    }
}
