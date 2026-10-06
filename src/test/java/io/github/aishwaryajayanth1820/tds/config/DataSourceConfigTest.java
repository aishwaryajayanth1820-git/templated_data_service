package io.github.aishwaryajayanth1820.tds.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DataSourceConfigTest {

    @Test
    void extractsFileFromSqliteUrl() {
        assertThat(DataSourceConfig.sqliteFile("jdbc:sqlite:./data/tds.db")).contains(Path.of("./data/tds.db"));
        assertThat(DataSourceConfig.sqliteFile("jdbc:sqlite:C:/x/y.db?cache=shared")).contains(Path.of("C:/x/y.db"));
    }

    @Test
    void ignoresInMemoryAndUriForms() {
        assertThat(DataSourceConfig.sqliteFile("jdbc:sqlite::memory:")).isEmpty();
        assertThat(DataSourceConfig.sqliteFile("jdbc:sqlite:file:test?mode=memory")).isEmpty();
    }
}
