package com.lnw.tds.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;

import com.lnw.tds.IntegrationTestBase;
import com.lnw.tds.data.RecordService;
import com.lnw.tds.web.ApiException;
import com.lnw.tds.web.ErrorCode;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Template lifecycle on real SQLite: create, publish, evolve with data, blocked publishes (ADR-0013). */
class PublishIT extends IntegrationTestBase {

    @Autowired DraftService drafts;
    @Autowired PublishService publisher;
    @Autowired CatalogService catalog;
    @Autowired RecordService records;
    @Autowired JsonMapper mapper;

    private static final String V1 = """
            {"grammar":"tds/v1","name":"it_items","label":"Items","manageType":"MANAGE_VIEW",
             "fields":[{"name":"id","type":"id"},
                       {"name":"item_name","type":"string","length":100,"required":true},
                       {"name":"colour","type":"enum","values":["RED","BLUE"]},
                       {"name":"note","type":"text"}]}""";

    @Test
    void seedTemplatesAreAutoPublishedWithSampleRows() {
        assertThat(catalog.snapshot().templates()).containsKeys("alerts", "alert_groups", "operator_settings");
        assertThat(records.list("alerts", new LinkedMultiValueMap<>(), ADMIN).total()).isEqualTo(12);
    }

    @Test
    void evolvesATableAndKeepsItsData() {
        drafts.create(V1, "admin");
        assertThat(publisher.publish("it_items", new PublishService.PublishRequest(null, null), "admin")).isEqualTo(1);
        ObjectNode row = mapper.createObjectNode().put("item_name", "Widget").put("colour", "RED").put("note", "first");
        records.create("it_items", row, ADMIN);
        long count = records.list("it_items", new LinkedMultiValueMap<>(), ADMIN).total();

        // v2: rename item_name → title, add enum value, drop note (destructive)
        ObjectNode v2 = (ObjectNode) mapper.readTree(V1);
        var fields = v2.withArray("fields");
        ((ObjectNode) fields.get(1)).put("name", "title").put("renamedFrom", "item_name");
        ((tools.jackson.databind.node.ArrayNode) fields.get(2).get("values")).add("GREEN");
        fields.remove(3);
        drafts.save("it_items", mapper.writeValueAsString(v2), null, "admin");
        PublishService.PlanView plan = publisher.plan("it_items");
        assertThat(plan.needsConfirmation()).isTrue();
        assertThat(plan.steps()).extracting(s -> s.description())
                .anyMatch(d -> d.contains("Rename column item_name → title"))
                .anyMatch(d -> d.contains("Drop column note"));

        assertThatThrownBy(() -> publisher.publish("it_items", new PublishService.PublishRequest(null, null), "admin"))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PUBLISH_BLOCKED);

        assertThat(publisher.publish("it_items", new PublishService.PublishRequest(null, "it_items"), "admin")).isEqualTo(2);
        var page = records.list("it_items", new LinkedMultiValueMap<>(), ADMIN);
        assertThat(page.total()).isEqualTo(count);
        assertThat(page.items().getFirst().path("title").asString()).isEqualTo("Widget");
        assertThat(page.items().getFirst().has("note")).isFalse();
        assertThat(drafts.get("it_items").json().toString()).doesNotContain("renamedFrom");
        assertThat(drafts.versions("it_items")).hasSize(2);
    }

    @Test
    void preChecksBlockChangesThatExistingRowsWouldBreak() {
        drafts.create(V1.replace("it_items", "it_checked"), "admin");
        publisher.publish("it_checked", new PublishService.PublishRequest(null, null), "admin");
        records.create("it_checked", mapper.createObjectNode().put("item_name", "No colour"), ADMIN);

        drafts.save("it_checked", V1.replace("it_items", "it_checked")
                .replace("{\"name\":\"colour\",\"type\":\"enum\",", "{\"name\":\"colour\",\"type\":\"enum\",\"required\":true,"), null, "admin");
        PublishService.PlanView plan = publisher.plan("it_checked");

        assertThat(plan.publishable()).isFalse();
        assertThat(plan.blockers()).anyMatch(b -> b.contains("Rows with no colour: 1 row(s)"));
        assertThatThrownBy(() -> publisher.publish("it_checked", new PublishService.PublishRequest(null, null), "admin"))
                .isInstanceOf(ApiException.class);
        assertThat(catalog.require("it_checked").version()).isEqualTo(1);
    }

    @Test
    void draftsWithErrorsCannotBePublishedAndStaleSavesConflict() {
        drafts.create(V1.replace("it_items", "it_errors"), "admin");
        var saved = drafts.save("it_errors", V1.replace("it_items", "it_errors").replace("\"type\":\"id\"", "\"type\":\"string\""), null, "admin");
        assertThat(saved.issues()).anyMatch(i -> i.code().equals("E002"));
        assertThatThrownBy(() -> publisher.publish("it_errors", new PublishService.PublishRequest(null, null), "admin"))
                .isInstanceOf(ApiException.class).hasMessageContaining("validation error");
        assertThatThrownBy(() -> drafts.save("it_errors", V1.replace("it_items", "it_errors"), "stale-checksum", "admin"))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CONFLICT_VERSION);
    }

}
