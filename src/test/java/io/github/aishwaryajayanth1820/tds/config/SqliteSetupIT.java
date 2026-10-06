package io.github.aishwaryajayanth1820.tds.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.aishwaryajayanth1820.tds.IntegrationTestBase;
import io.github.aishwaryajayanth1820.tds.jdbc.DbVendor;
import io.github.aishwaryajayanth1820.tds.jdbc.DbVendorResolver;

class SqliteSetupIT extends IntegrationTestBase {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DbVendorResolver vendor;

    @Test
    void connectionsHaveRequiredPragmas() {
        assertThat(jdbc.sql("PRAGMA foreign_keys").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualToIgnoringCase("wal");
        assertThat(jdbc.sql("PRAGMA busy_timeout").query(Integer.class).single()).isEqualTo(5000);
    }

    @Test
    void detectsSqlite() {
        assertThat(vendor.current()).isEqualTo(DbVendor.SQLITE);
    }

    @Test
    void flywayCreatesSystemTablesAndBuiltInRoles() {
        List<String> tables = jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'tds_%' ORDER BY name")
                .query(String.class).list();
        assertThat(tables).containsExactly("tds_action_log", "tds_role", "tds_template", "tds_template_version",
                "tds_user", "tds_user_role");
        assertThat(jdbc.sql("SELECT name FROM tds_role WHERE builtin = 1 ORDER BY name").query(String.class).list())
                .containsExactly("admin", "viewer");
    }

    @Test
    void foreignKeysAreEnforced() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.sql("INSERT INTO tds_user_role (user_id, role_id) VALUES (999999, 1)").update())
            .hasMessageContaining("FOREIGN KEY");
    }
}
