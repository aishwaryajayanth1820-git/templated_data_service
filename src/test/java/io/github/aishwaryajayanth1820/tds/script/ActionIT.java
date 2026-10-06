package io.github.aishwaryajayanth1820.tds.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.aishwaryajayanth1820.tds.IntegrationTestBase;
import io.github.aishwaryajayanth1820.tds.catalog.CatalogService;
import io.github.aishwaryajayanth1820.tds.data.RecordService;
import io.github.aishwaryajayanth1820.tds.grammar.Model.ActionPlacement;
import io.github.aishwaryajayanth1820.tds.grammar.Model.ActionSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;
import io.github.aishwaryajayanth1820.tds.web.ApiException;
import io.github.aishwaryajayanth1820.tds.web.ErrorCode;

/** Action scripts in the GraalJS sandbox (ADR-0006). */
class ActionIT extends IntegrationTestBase {

    @Autowired ActionRunner runner;
    @Autowired ScriptRegistry scripts;
    @Autowired CatalogService catalog;
    @Autowired RecordService records;

    private Template alerts() {
        return catalog.require("alerts").model();
    }

    private ActionSpec action(String script, String fn, int timeoutMs) {
        return new ActionSpec("test", "Test", ActionPlacement.ROW, script, fn, null, null, timeoutMs, null);
    }

    @Test
    void createTicketBuildsTextAndWritesTheFile() throws Exception {
        RecordService.Loaded row = records.loadForAction(alerts(), 1, VIEWER);
        ActionRunner.ActionResult r = runner.run(alerts(), alerts().action("create_ticket").orElseThrow(), 1L, row.values(), null,
                row.display(), VIEWER);

        assertThat(r.kind()).isEqualTo("text");
        assertThat(r.title()).isEqualTo("[Major] Packet loss above 5% on edge-02");
        assertThat(r.content()).contains("|Group|Network|", "Raised by vera");
        assertThat(Files.readString(workDir().resolve("action-output/alerts/ticket-alert-1.txt"))).isEqualTo(r.content());
    }

    @Test
    void scriptsCannotReachJavaAndBadOutputNamesAreRefused() {
        scripts.write("tests/evil.js", """
                function hostClass(ctx) { return { kind: "message", content: String(Java.type("java.lang.System")) }; }
                function traverse(ctx) { ctx.files.writeText("../../escape.txt", "x"); return { kind: "message", content: "?" }; }
                function mutate(ctx) { try { ctx.record.alert_name = "changed"; } catch (e) {} return { kind: "message", content: ctx.record.alert_name }; }
                """);
        Map<String, Object> rec = Map.of("id", 1L, "alert_name", "a");

        assertThatThrownBy(() -> runner.run(alerts(), action("tests/evil.js", "hostClass", 2000), 1L, rec, null, Map.of(), ADMIN))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ACTION_FAILED));
        assertThatThrownBy(() -> runner.run(alerts(), action("tests/evil.js", "traverse", 2000), 1L, rec, null, Map.of(), ADMIN))
                .isInstanceOf(ApiException.class).hasMessageContaining("invalid file name");
        assertThat(runner.run(alerts(), action("tests/evil.js", "mutate", 2000), 1L, rec, null, Map.of(), ADMIN).content())
                .as("ctx.record is read-only").isEqualTo("a");
    }

    @Test
    void runawayScriptsAreCancelledAtTheirTimeout() {
        scripts.write("tests/loop.js", "function spin(ctx) { while (true) {} }");
        long t0 = System.nanoTime();
        assertThatThrownBy(() -> runner.run(alerts(), action("tests/loop.js", "spin", 300), 1L, Map.of("id", 1L), null, Map.of(), ADMIN))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ACTION_TIMEOUT));
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(5000);
    }

    @Test
    void brokenEditsKeepTheLastGoodVersion() {
        scripts.write("tests/evolving.js", "function hello(ctx) { return { kind: \"message\", content: \"v1\" }; }");
        scripts.write("tests/evolving.js", "function hello(ctx) { return { kind: ");

        ScriptRegistry.ScriptFile f = scripts.get("tests/evolving.js").orElseThrow();
        assertThat(f.status()).isEqualTo(ScriptRegistry.Status.ERROR);
        assertThat(f.functions()).contains("hello");
        assertThat(runner.run(alerts(), action("tests/evolving.js", "hello", 2000), 1L, Map.of("id", 1L), null, Map.of(), ADMIN).content())
                .isEqualTo("v1");
    }
}
