package com.lnw.tds.script;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.lnw.tds.config.TdsProperties;
import com.lnw.tds.data.ValueCodec;
import com.lnw.tds.grammar.Model.ActionSpec;
import com.lnw.tds.grammar.Model.Template;
import com.lnw.tds.jdbc.DbVendorResolver;
import com.lnw.tds.security.TdsPrincipal;
import com.lnw.tds.web.ApiException;
import com.lnw.tds.web.ErrorCode;

import jakarta.annotation.PreDestroy;

/**
 * Runs an action function in a fresh, sandboxed GraalJS context (ADR-0006): no host access, IO, threads or
 * environment; {@code ctx} (plain values) is the only bridge; a watchdog cancels the run at {@code timeoutMs}.
 */
@Service
public class ActionRunner {

    private static final Logger log = LoggerFactory.getLogger(ActionRunner.class);
    private static final Set<String> KINDS = Set.of("text", "download", "message", "refresh");

    /** Result returned to the UI (grammar §8). */
    public record ActionResult(String kind, String title, String content, String file) {}

    private final ScriptRegistry scripts;
    private final ActionOutputStore output;
    private final JdbcClient jdbc;
    private final DbVendorResolver vendor;
    private final Semaphore permits;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("tds-action-watchdog").factory());

    ActionRunner(ScriptRegistry scripts, ActionOutputStore output, JdbcClient jdbc, DbVendorResolver vendor, TdsProperties properties) {
        this.scripts = scripts;
        this.output = output;
        this.jdbc = jdbc;
        this.vendor = vendor;
        this.permits = new Semaphore(Math.max(1, properties.scripts().maxConcurrent()));
    }

    @PreDestroy
    void shutdown() {
        watchdog.shutdownNow();
    }

    /**
     * @param record  readable values of the row ({@code row} actions), or null
     * @param records rows for {@code selection} / {@code toolbar} actions, or null
     * @param display display texts for ref / enum fields of {@code record}
     */
    public ActionResult run(Template t, ActionSpec a, Long recordId, Map<String, Object> record, List<Map<String, Object>> records,
                            Map<String, String> display, TdsPrincipal p) {
        ScriptRegistry.ScriptFile file = scripts.get(a.script()).filter(f -> f.source() != null && f.functions().contains(a.function()))
                .orElseThrow(() -> new ApiException(ErrorCode.ACTION_FAILED, "Action unavailable",
                        "Script " + a.script() + "#" + a.function() + " is not loaded"));
        if (!permits.tryAcquire()) {
            throw new ApiException(ErrorCode.TOO_MANY_ACTIONS, "Busy", "Too many actions are running. Try again shortly");
        }
        Instant started = Instant.now();
        long t0 = System.nanoTime();
        String status = "OK";
        String message = null;
        try {
            Context context = Context.newBuilder("js").engine(scripts.engine())
                    .allowHostAccess(HostAccess.NONE).allowHostClassLookup(n -> false).allowIO(IOAccess.NONE)
                    .allowCreateThread(false).allowNativeAccess(false).allowEnvironmentAccess(EnvironmentAccess.NONE)
                    .build();
            ScheduledFuture<?> timer = watchdog.schedule(() -> context.close(true), a.timeoutMs(), TimeUnit.MILLISECONDS);
            try {
                context.eval(file.source());
                Value fn = context.getBindings("js").getMember(a.function());
                Value result = fn.execute(ctx(t, a, record, records, display, p));
                return toResult(result);
            } finally {
                timer.cancel(false);
                try {
                    context.close();
                } catch (PolyglotException ignored) {
                    // already cancelled by the watchdog
                }
            }
        } catch (PolyglotException e) {
            if (e.isCancelled()) {
                status = "TIMEOUT";
                message = "Exceeded " + a.timeoutMs() + " ms";
                throw new ApiException(ErrorCode.ACTION_TIMEOUT, "Action timed out", a.label() + " did not finish within " + a.timeoutMs() + " ms");
            }
            status = "ERROR";
            message = e.getMessage();
            throw new ApiException(ErrorCode.ACTION_FAILED, "Action failed", a.label() + " failed: " + e.getMessage());
        } catch (ApiException e) {
            status = "ERROR";
            message = e.getMessage();
            throw e;
        } finally {
            permits.release();
            int ms = (int) ((System.nanoTime() - t0) / 1_000_000);
            logRun(t.name(), a.name(), recordId, p.username(), started, ms, status, message);
        }
    }

    private ProxyObject ctx(Template t, ActionSpec a, Map<String, Object> record, List<Map<String, Object>> records,
                            Map<String, String> display, TdsPrincipal p) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("record", record == null ? null : readOnly(record));
        if (records != null) {
            ctx.put("records", ProxyArray.fromList(new ArrayList<>(records.stream().map(r -> (Object) readOnly(r)).toList())));
        }
        ctx.put("display", readOnly(new HashMap<>(display == null ? Map.of() : display)));
        ctx.put("user", readOnly(Map.of("username", p.username(),
                "roles", ProxyArray.fromList(new ArrayList<>(p.roles())))));
        ctx.put("template", readOnly(Map.of("name", t.name(), "label", t.displayLabel())));
        ctx.put("now", (ProxyExecutable) args -> ValueCodec.nowIso());
        ctx.put("files", readOnly(Map.of("writeText", (ProxyExecutable) args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("files.writeText(name, text) needs two arguments");
            }
            return output.writeText(t.name(), args[0].asString(), args[1].isString() ? args[1].asString() : args[1].toString());
        })));
        ctx.put("log", readOnly(Map.of(
                "info", (ProxyExecutable) args -> {
                    log.info("[{}.{}] {}", t.name(), a.name(), args.length > 0 ? args[0] : "");
                    return null;
                },
                "warn", (ProxyExecutable) args -> {
                    log.warn("[{}.{}] {}", t.name(), a.name(), args.length > 0 ? args[0] : "");
                    return null;
                })));
        return readOnly(ctx);
    }

    /** A frozen view: scripts can read but not modify what they are given. */
    private static ProxyObject readOnly(Map<String, ?> values) {
        Map<String, Object> converted = new HashMap<>();
        values.forEach((k, v) -> converted.put(k, guest(v)));
        return new ProxyObject() {
            @Override
            public Object getMember(String key) {
                return converted.get(key);
            }

            @Override
            public Object getMemberKeys() {
                return ProxyArray.fromArray(converted.keySet().toArray());
            }

            @Override
            public boolean hasMember(String key) {
                return converted.containsKey(key);
            }

            @Override
            public void putMember(String key, Value value) {
                throw new UnsupportedOperationException("ctx is read-only");
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Object guest(Object v) {
        return switch (v) {
            case null -> null;
            case BigDecimal b -> b.doubleValue();
            case Map<?, ?> m -> readOnly((Map<String, ?>) m);
            case List<?> l -> ProxyArray.fromList(new ArrayList<>(l.stream().map(ActionRunner::guest).toList()));
            default -> v;
        };
    }

    private static ActionResult toResult(Value r) {
        if (r == null || r.isNull() || !r.hasMembers()) {
            throw new ApiException(ErrorCode.ACTION_FAILED, "Action failed", "The action did not return a result object");
        }
        String kind = str(r, "kind");
        if (kind == null || !KINDS.contains(kind)) {
            throw new ApiException(ErrorCode.ACTION_FAILED, "Action failed", "Result kind must be one of " + KINDS);
        }
        return new ActionResult(kind, str(r, "title"), str(r, "content"), str(r, "file"));
    }

    private static String str(Value r, String key) {
        if (!r.hasMember(key)) {
            return null;
        }
        Value v = r.getMember(key);
        return v == null || v.isNull() ? null : v.isString() ? v.asString() : v.toString();
    }

    private void logRun(String template, String action, Long recordId, String user, Instant started, int ms, String status,
                        String message) {
        try {
            jdbc.sql("""
                    INSERT INTO tds_action_log (template_name, action_name, record_id, username, started_at, duration_ms, status, message)
                    VALUES (:t, :a, :r, :u, :s, :d, :st, :m)""")
                    .param("t", template).param("a", action).param("r", recordId).param("u", user)
                    .param("s", vendor.current() == com.lnw.tds.jdbc.DbVendor.SQLITE ? ValueCodec.INSTANT_MILLIS.format(started)
                            : java.time.OffsetDateTime.ofInstant(started, java.time.ZoneOffset.UTC))
                    .param("d", ms).param("st", status).param("m", message).update();
        } catch (RuntimeException e) {
            log.warn("Could not write action log: {}", e.getMessage());
        }
    }
}
