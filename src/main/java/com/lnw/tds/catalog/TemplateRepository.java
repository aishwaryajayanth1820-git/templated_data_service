package com.lnw.tds.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lnw.tds.jdbc.DbVendorResolver;
import com.lnw.tds.jdbc.JdbcTime;

/** {@code tds_template} (drafts) and {@code tds_template_version} (published history). */
@Repository
class TemplateRepository {

    record Row(long id, String name, String manageType, String draftJson, String draftChecksum, Integer publishedVersion,
               Instant updatedAt, String updatedBy) {}

    record VersionRow(String name, int version, String json, String checksum, String planJson, String appliedSql,
                      Instant publishedAt, String publishedBy) {}

    private final JdbcClient jdbc;
    private final DbVendorResolver vendor;

    TemplateRepository(JdbcClient jdbc, DbVendorResolver vendor) {
        this.jdbc = jdbc;
        this.vendor = vendor;
    }

    List<Row> findAll() {
        return jdbc.sql("SELECT * FROM tds_template ORDER BY name").query(TemplateRepository::row).list();
    }

    Optional<Row> find(String name) {
        return jdbc.sql("SELECT * FROM tds_template WHERE name = :n").param("n", name).query(TemplateRepository::row).optional();
    }

    long insert(String name, String manageType, String json, String checksum, String user) {
        return jdbc.sql("""
                INSERT INTO tds_template (name, manage_type, draft_json, draft_checksum, updated_by)
                VALUES (:n, :m, :j, :c, :u) RETURNING id""")
                .param("n", name).param("m", manageType).param("j", json).param("c", checksum).param("u", user)
                .query(Long.class).single();
    }

    void updateDraft(JdbcClient client, String name, String manageType, String json, String checksum, String user) {
        client.sql("UPDATE tds_template SET manage_type = :m, draft_json = :j, draft_checksum = :c, updated_by = :u, updated_at = "
                        + vendor.current().nowSql() + " WHERE name = :n")
                .param("m", manageType).param("j", json).param("c", checksum).param("u", user).param("n", name).update();
    }

    void updateDraft(String name, String manageType, String json, String checksum, String user) {
        updateDraft(jdbc, name, manageType, json, checksum, user);
    }

    void markPublished(JdbcClient client, String name, int version) {
        client.sql("UPDATE tds_template SET published_version = :v WHERE name = :n").param("v", version).param("n", name).update();
    }

    void insertVersion(JdbcClient client, long templateId, int version, String json, String checksum, String plan, String sql,
                       String user) {
        client.sql("""
                INSERT INTO tds_template_version (template_id, version, template_json, checksum, plan_json, applied_sql, published_by)
                VALUES (:t, :v, :j, :c, :p, :s, :u)""")
                .param("t", templateId).param("v", version).param("j", json).param("c", checksum).param("p", plan)
                .param("s", sql).param("u", user).update();
    }

    List<VersionRow> versions(String name) {
        return jdbc.sql("""
                SELECT t.name, v.* FROM tds_template_version v JOIN tds_template t ON t.id = v.template_id
                WHERE t.name = :n ORDER BY v.version DESC""").param("n", name).query(TemplateRepository::version).list();
    }

    /** The currently published version of every template. */
    Map<String, VersionRow> latestPublished() {
        Map<String, VersionRow> out = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT t.name, v.* FROM tds_template t
                JOIN tds_template_version v ON v.template_id = t.id AND v.version = t.published_version
                ORDER BY t.name""").query(TemplateRepository::version).list().forEach(v -> out.put(v.name(), v));
        return out;
    }

    void delete(String name) {
        jdbc.sql("DELETE FROM tds_template WHERE name = :n").param("n", name).update();
    }

    private static Row row(ResultSet rs, int i) throws SQLException {
        int pv = rs.getInt("published_version");
        Integer published = rs.wasNull() ? null : pv;   // wasNull() refers to the last column read
        return new Row(rs.getLong("id"), rs.getString("name"), rs.getString("manage_type"), rs.getString("draft_json"),
                rs.getString("draft_checksum"), published, JdbcTime.toInstant(rs.getObject("updated_at")),
                rs.getString("updated_by"));
    }

    private static VersionRow version(ResultSet rs, int i) throws SQLException {
        return new VersionRow(rs.getString("name"), rs.getInt("version"), rs.getString("template_json"), rs.getString("checksum"),
                rs.getString("plan_json"), rs.getString("applied_sql"), JdbcTime.toInstant(rs.getObject("published_at")),
                rs.getString("published_by"));
    }
}
