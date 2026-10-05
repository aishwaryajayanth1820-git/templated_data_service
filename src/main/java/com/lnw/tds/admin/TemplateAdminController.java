package com.lnw.tds.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lnw.tds.catalog.DraftService;
import com.lnw.tds.catalog.PublishService;
import com.lnw.tds.grammar.Issue;
import com.lnw.tds.security.CurrentPrincipal;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Schema Studio API (architecture §8.3). Admin only (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/templates")
class TemplateAdminController {

    record PublishResult(String name, int version) {}

    private final DraftService drafts;
    private final PublishService publisher;
    private final JsonMapper mapper;

    TemplateAdminController(DraftService drafts, PublishService publisher, JsonMapper mapper) {
        this.drafts = drafts;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @GetMapping
    List<DraftService.DraftView> list() {
        return drafts.list();
    }

    @PostMapping
    DraftService.DraftView create(@RequestBody String json) {
        return drafts.create(json, user());
    }

    /** Creates the template, or replaces the draft when it exists already. */
    @PostMapping("/import")
    DraftService.DraftView importTemplate(@RequestBody String json) {
        String name = drafts.readObjectName(json);
        if (drafts.exists(name)) {
            return drafts.save(name, json, null, user());
        }
        return drafts.create(json, user());
    }

    @GetMapping("/{name}")
    DraftService.DraftView get(@PathVariable String name) {
        return drafts.get(name);
    }

    /** {@code If-Match: <checksum>} protects against overwriting a concurrent edit. */
    @PutMapping("/{name}")
    DraftService.DraftView save(@PathVariable String name, @RequestBody String json,
                                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String checksum) {
        return drafts.save(name, json, checksum == null ? null : checksum.replace("\"", ""), user());
    }

    /** Validates an unsaved text (live feedback in the Studio). */
    @PostMapping("/validate")
    List<Issue> validate(@RequestBody String json) {
        return drafts.validate(json).issues();
    }

    @GetMapping("/{name}/plan")
    PublishService.PlanView plan(@PathVariable String name) {
        return publisher.plan(name);
    }

    @PostMapping("/{name}/publish")
    PublishResult publish(@PathVariable String name, @RequestBody PublishService.PublishRequest request) {
        return new PublishResult(name, publisher.publish(name, request, user()));
    }

    @GetMapping("/{name}/versions")
    List<DraftService.VersionView> versions(@PathVariable String name) {
        return drafts.versions(name);
    }

    @GetMapping("/{name}/export")
    ResponseEntity<String> export(@PathVariable String name) {
        ObjectNode doc = mapper.createObjectNode();
        doc.put("$schema", "../schema/tds-template.schema.json");
        drafts.get(name).json().properties().forEach(e -> {
            if (!e.getKey().equals("$schema")) {
                doc.set(e.getKey(), e.getValue());
            }
        });
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name + ".json").build().toString())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc) + "\n");
    }

    @DeleteMapping("/{name}")
    ResponseEntity<Void> delete(@PathVariable String name) {
        drafts.delete(name);
        return ResponseEntity.noContent().build();
    }

    static Map<String, Object> none() {
        return Map.of();
    }

    private static String user() {
        return CurrentPrincipal.get().username();
    }
}
