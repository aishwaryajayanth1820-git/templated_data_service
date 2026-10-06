package io.github.aishwaryajayanth1820.tds.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import io.github.aishwaryajayanth1820.tds.IntegrationTestBase;
import io.github.aishwaryajayanth1820.tds.catalog.DraftService;
import io.github.aishwaryajayanth1820.tds.catalog.PublishService;
import io.github.aishwaryajayanth1820.tds.web.ApiException;
import io.github.aishwaryajayanth1820.tds.web.ErrorCode;
import io.github.aishwaryajayanth1820.tds.web.ValidationException;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** The generic data API on the seed tables and a template with field-level access (architecture §8.2). */
class RecordServiceIT extends IntegrationTestBase {

    @Autowired RecordService records;
    @Autowired DraftService drafts;
    @Autowired PublishService publisher;
    @Autowired JsonMapper mapper;

    private static MultiValueMap<String, String> q(String... kv) {
        LinkedMultiValueMap<String, String> m = new LinkedMultiValueMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.add(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void listsWithDefaultSortRefDisplaysFiltersSearchAndRuleFlags() {
        var page = records.list("alerts", q("size", "5"), VIEWER);
        assertThat(page.total()).isEqualTo(12);
        assertThat(page.items()).hasSize(5);
        assertThat(page.items().getFirst().path("alert_date").asString()).isEqualTo("2026-10-03T09:42:00.000Z");
        assertThat(page.items().getFirst().path("alert_group$display").asString()).isEqualTo("Network");

        var critical = records.list("alerts", q("f.alert_type", "CRITICAL"), VIEWER);
        assertThat(critical.total()).isEqualTo(3);
        assertThat(critical.items()).filteredOn(i -> i.has("$issues")).hasSize(1);

        assertThat(records.list("alerts", q("q", "payment"), VIEWER).total()).isEqualTo(1);
        assertThat(records.list("alerts", q("f.alert_date.gte", "2026-10-03"), VIEWER).total()).isEqualTo(3);
        assertThatThrownBy(() -> records.list("alerts", q("f.nope", "x"), VIEWER)).isInstanceOf(ApiException.class);
    }

    @Test
    void viewerCanReadButNotWriteManagedTables() {
        assertThat(records.list("operator_settings", q(), VIEWER).total()).isGreaterThan(0);
        assertThatThrownBy(() -> records.create("operator_settings", mapper.createObjectNode().put("operator_name", "x"), VIEWER))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> records.list("alert_groups", q(), VIEWER))
                .as("viewer has no access to DATA_SOURCE by default").isInstanceOf(ApiException.class);
    }

    @Test
    void createValidatesUpdatesWithOptimisticLockAndTranslatesDbErrors() {
        assertThatThrownBy(() -> records.create("operator_settings", mapper.createObjectNode().put("min_spin_time", "abc"), ADMIN))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.properties().get("errors").toString())
                        .contains("min_spin_time=Must be a number"));
        assertThatThrownBy(() -> records.create("operator_settings", body("Acme Gaming", "UK", 1), ADMIN))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CONFLICT_UNIQUE);

        ObjectNode created = records.create("operator_settings", body("Delta Bet", "Malta", 1.5), ADMIN);
        long id = created.path("id").asLong();
        assertThat(created.path("row_version").asLong()).isZero();
        assertThat(created.path("created_by").asString()).isEqualTo("admin");

        ObjectNode patch = mapper.createObjectNode().put("row_version", 0).put("min_spin_time", 2);
        assertThat(records.update("operator_settings", id, patch, true, ADMIN).path("row_version").asLong()).isEqualTo(1);
        assertThatThrownBy(() -> records.update("operator_settings", id, patch, true, ADMIN))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CONFLICT_VERSION);

        assertThatThrownBy(() -> records.delete("alert_groups", 1, ADMIN))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CONFLICT_REFERENCE);
        records.delete("operator_settings", id, ADMIN);
        assertThatThrownBy(() -> records.get("operator_settings", id, ADMIN)).isInstanceOf(ApiException.class);
    }

    @Test
    void fieldAccessHidesAndProtectsFields() {
        drafts.create("""
                {"grammar":"tds/v1","name":"it_secrets","manageType":"MANAGE_VIEW",
                 "fields":[{"name":"id","type":"id"},{"name":"title","type":"string"},
                           {"name":"secret","type":"string","access":{"viewer":[]}},
                           {"name":"status","type":"string","access":{"viewer":["read"]}}],
                 "access":{"admin":["read","create","update","delete"],"viewer":["read","create","update"]}}""", "admin");
        publisher.publish("it_secrets", new PublishService.PublishRequest(null, null), "admin");
        long id = records.create("it_secrets", mapper.createObjectNode().put("title", "a").put("secret", "s3").put("status", "new"), ADMIN)
                .path("id").asLong();

        ObjectNode seen = records.get("it_secrets", id, VIEWER);
        assertThat(seen.has("secret")).isFalse();
        assertThat(seen.path("status").asString()).isEqualTo("new");
        assertThatThrownBy(() -> records.update("it_secrets", id, mapper.createObjectNode().put("row_version", 0).put("status", "done"), true, VIEWER))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.properties().get("errors").toString()).contains("status"));
        assertThatThrownBy(() -> records.list("it_secrets", q("f.secret", "s3"), VIEWER)).isInstanceOf(ApiException.class);
        assertThat(records.update("it_secrets", id, mapper.createObjectNode().put("row_version", 0).put("title", "b"), true, VIEWER)
                .path("title").asString()).isEqualTo("b");
    }

    @Test
    void lookupOptionsComeFromTheReferencedTable() {
        assertThat(records.options("alerts", "alert_group", "pay", VIEWER)).extracting(RecordService.Option::label).containsExactly("Payments");
        assertThat(records.options("alerts", "alert_group", "", VIEWER)).hasSize(5);
    }

    private ObjectNode body(String operator, String jurisdiction, double spin) {
        return mapper.createObjectNode().put("operator_name", operator).put("jurisdictional_name", jurisdiction).put("min_spin_time", spin);
    }

}
