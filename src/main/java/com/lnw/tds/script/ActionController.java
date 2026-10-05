package com.lnw.tds.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lnw.tds.access.AccessEvaluator;
import com.lnw.tds.catalog.CatalogService;
import com.lnw.tds.data.RecordService;
import com.lnw.tds.grammar.JsonLogic;
import com.lnw.tds.grammar.Model.ActionPlacement;
import com.lnw.tds.grammar.Model.ActionSpec;
import com.lnw.tds.grammar.Model.Template;
import com.lnw.tds.grammar.RecordValidator;
import com.lnw.tds.security.CurrentPrincipal;
import com.lnw.tds.security.TdsPrincipal;
import com.lnw.tds.web.ApiException;
import com.lnw.tds.web.ErrorCode;

/** Action endpoints (architecture §8.2): row actions, selection/toolbar actions, and output downloads. */
@RestController
@RequestMapping("/api/data/{table}")
class ActionController {

    record BulkRequest(List<Long> ids) {}

    private final CatalogService catalog;
    private final AccessEvaluator access;
    private final RecordService records;
    private final ActionRunner runner;
    private final ActionOutputStore output;

    ActionController(CatalogService catalog, AccessEvaluator access, RecordService records, ActionRunner runner,
                     ActionOutputStore output) {
        this.catalog = catalog;
        this.access = access;
        this.records = records;
        this.runner = runner;
        this.output = output;
    }

    @PostMapping("/{id:\\d+}/actions/{action}")
    ActionRunner.ActionResult runRow(@PathVariable String table, @PathVariable long id, @PathVariable String action) {
        TdsPrincipal p = CurrentPrincipal.get();
        Template t = catalog.require(table).model();
        ActionSpec a = action(t, action, p, ActionPlacement.ROW);
        RecordService.Loaded row = records.loadForAction(t, id, p);
        if (a.visibleWhen() != null && !JsonLogic.test(a.visibleWhen(),
                RecordValidator.context(row.values(), RecordValidator.WriteOp.UPDATE, p.username(), p.roles()))) {
            throw new ApiException(ErrorCode.ACTION_FAILED, "Action not available", a.label() + " is not available for this row");
        }
        return runner.run(t, a, id, row.values(), null, row.display(), p);
    }

    @PostMapping("/actions/{action}")
    ActionRunner.ActionResult runBulk(@PathVariable String table, @PathVariable String action,
                                      @RequestBody(required = false) BulkRequest body) {
        TdsPrincipal p = CurrentPrincipal.get();
        Template t = catalog.require(table).model();
        ActionSpec a = t.action(action).orElseThrow(() -> ApiException.notFound("Action " + action));
        if (a.placement() == ActionPlacement.ROW) {
            throw ApiException.badRequest(a.label() + " runs on a single row: POST /api/data/" + table + "/{id}/actions/" + action);
        }
        action(t, action, p, a.placement());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (a.placement() == ActionPlacement.SELECTION) {
            List<Long> ids = body == null || body.ids() == null ? List.of() : body.ids();
            if (ids.isEmpty()) {
                throw ApiException.badRequest("Select at least one row");
            }
            for (Long id : ids) {
                rows.add(records.loadForAction(t, id, p).values());
            }
        }
        return runner.run(t, a, null, null, a.placement() == ActionPlacement.SELECTION ? rows : null, Map.of(), p);
    }

    @GetMapping("/action-output/{file}")
    ResponseEntity<Resource> download(@PathVariable String table, @PathVariable String file) throws IOException {
        Template t = catalog.require(table).model();
        access.require(t, CurrentPrincipal.get(), AccessEvaluator.READ);
        Path path = output.read(t.name(), file);
        String type = Files.probeContentType(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file).build().toString())
                .contentType(type != null ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }

    private ActionSpec action(Template t, String name, TdsPrincipal p, ActionPlacement expected) {
        ActionSpec a = t.action(name).orElseThrow(() -> ApiException.notFound("Action " + name));
        if (a.placement() != expected) {
            throw ApiException.badRequest("Action " + name + " is a " + a.placement().name().toLowerCase(java.util.Locale.ROOT) + " action");
        }
        if (!access.canRun(t, p, name)) {
            throw ApiException.forbidden("Your roles do not allow running " + a.label());
        }
        return a;
    }
}
