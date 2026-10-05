package com.lnw.tds.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lnw.tds.ddl.DdlGenerator;
import com.lnw.tds.ddl.MigrationExecutor;
import com.lnw.tds.ddl.MigrationExecutor.PreCheckResult;
import com.lnw.tds.ddl.MigrationPlan;
import com.lnw.tds.ddl.MigrationPlanner;
import com.lnw.tds.ddl.SqlDialect;
import com.lnw.tds.grammar.Issue;
import com.lnw.tds.grammar.Model.Template;
import com.lnw.tds.jdbc.DbVendor;
import com.lnw.tds.web.ApiException;
import com.lnw.tds.web.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Plan and publish a draft (architecture §7, ADR-0013). Publishes are serialised. */
@Service
public class PublishService {

    private static final Logger log = LoggerFactory.getLogger(PublishService.class);

    public record PlanView(String name, Integer fromVersion, List<MigrationPlan.Step> steps, List<PreCheckResult> preChecks,
                           boolean rebuild, List<String> statements, Map<String, String> ddl, List<Issue> issues,
                           boolean publishable, List<String> blockers, boolean needsConfirmation) {}

    public record PublishRequest(String expectedChecksum, String confirm) {}

    private final DraftService drafts;
    private final TemplateRepository repository;
    private final CatalogService catalog;
    private final MigrationPlanner planner;
    private final MigrationExecutor executor;
    private final DdlGenerator ddl;
    private final JsonMapper mapper;
    private final ReentrantLock lock = new ReentrantLock();

    PublishService(DraftService drafts, TemplateRepository repository, CatalogService catalog, MigrationPlanner planner,
                   MigrationExecutor executor, DdlGenerator ddl, JsonMapper mapper) {
        this.drafts = drafts;
        this.repository = repository;
        this.catalog = catalog;
        this.planner = planner;
        this.executor = executor;
        this.ddl = ddl;
        this.mapper = mapper;
    }

    public PlanView plan(String name) {
        TemplateRepository.Row row = drafts.require(name);
        DraftService.Validated v = drafts.validate(row.draftJson());
        Map<String, String> previews = new LinkedHashMap<>();
        if (v.template() == null) {
            return new PlanView(name, row.publishedVersion(), List.of(), List.of(), false, List.of(), previews, v.issues(), false,
                    List.of("The draft has structural errors"), false);
        }
        previews.put("sqlite", new DdlGenerator(SqlDialect.of(DbVendor.SQLITE)).preview(v.template()));
        previews.put("postgresql", new DdlGenerator(SqlDialect.of(DbVendor.POSTGRESQL)).preview(v.template()));
        Template published = catalog.snapshot().find(name).map(CatalogService.Published::model).orElse(null);
        MigrationPlan plan = planner.plan(published, v.template());
        List<PreCheckResult> preChecks = executor.preCheck(plan);
        List<String> blockers = blockers(v, plan, preChecks, row);
        return new PlanView(name, row.publishedVersion(), plan.steps(), preChecks, plan.rebuild(), plan.statements(), previews,
                v.issues(), blockers.isEmpty(), blockers, plan.hasDestructive());
    }

    /** Applies the plan and returns the new version number. */
    public int publish(String name, PublishRequest request, String user) {
        lock.lock();
        try {
            TemplateRepository.Row row = drafts.require(name);
            if (request.expectedChecksum() != null && !request.expectedChecksum().equals(row.draftChecksum())) {
                throw ApiException.conflict(ErrorCode.CONFLICT_VERSION, "The draft changed since you reviewed the plan. Review it again");
            }
            DraftService.Validated v = drafts.validate(row.draftJson());
            Template published = catalog.snapshot().find(name).map(CatalogService.Published::model).orElse(null);
            MigrationPlan plan = v.template() == null ? null : planner.plan(published, v.template());
            List<PreCheckResult> preChecks = plan == null ? List.of() : executor.preCheck(plan);
            List<String> blockers = blockers(v, plan, preChecks, row);
            if (plan != null && plan.hasDestructive() && !name.equals(request.confirm())) {
                blockers.add("This change loses data: type the table name to confirm");
            }
            if (!blockers.isEmpty()) {
                throw new ApiException(ErrorCode.PUBLISH_BLOCKED, "Publish blocked", String.join("; ", blockers),
                        Map.of("blockers", blockers, "issues", v.issues()));
            }
            int version = row.publishedVersion() == null ? 1 : row.publishedVersion() + 1;
            ObjectNode doc = (ObjectNode) mapper.readTree(row.draftJson());
            stripRenames(doc);
            String json = drafts.pretty(doc);
            String checksum = DraftService.checksum(json);
            String planJson = mapper.writeValueAsString(Map.of("steps", plan.steps(), "preChecks", preChecks));
            try {
                executor.apply(plan.statements(), plan.rebuild(), tx -> {
                    repository.updateDraft(tx, name, row.manageType(), json, checksum, user);
                    repository.insertVersion(tx, row.id(), version, json, checksum, planJson, String.join(";\n", plan.statements()), user);
                    repository.markPublished(tx, name, version);
                });
            } catch (MigrationExecutor.MigrationFailedException e) {
                throw new ApiException(ErrorCode.PUBLISH_BLOCKED, "Publish failed",
                        "The database rejected the change, nothing was applied: " + e.getMessage(),
                        Map.of("blockers", List.of(e.getMessage())));
            }
            catalog.reload();
            log.info("Published {} v{} by {} ({} step(s), {} statement(s))", name, version, user, plan.steps().size(),
                    plan.statements().size());
            return version;
        } finally {
            lock.unlock();
        }
    }

    private List<String> blockers(DraftService.Validated v, MigrationPlan plan, List<PreCheckResult> preChecks,
                                  TemplateRepository.Row row) {
        List<String> out = new ArrayList<>();
        long errors = v.issues().stream().filter(Issue::isError).count();
        if (errors > 0) {
            out.add(errors + " validation error(s) must be fixed first");
        }
        if (plan == null) {
            return out;
        }
        plan.steps().stream().filter(s -> s.cls() == MigrationPlan.StepClass.BLOCKED).forEach(s -> out.add(s.description()));
        for (PreCheckResult c : preChecks) {
            if (c.violations() > 0) {
                out.add(c.description() + ": " + c.violations() + " row(s)");
            }
        }
        if (plan.isEmpty() && row.publishedVersion() != null) {
            out.add("Nothing to publish: the draft equals the published version");
        }
        return out;
    }

    private static void stripRenames(ObjectNode doc) {
        JsonNode fields = doc.get("fields");
        if (fields != null && fields.isArray()) {
            fields.values().forEach(f -> {
                if (f instanceof ObjectNode o) {
                    o.remove("renamedFrom");
                }
            });
        }
    }
}
