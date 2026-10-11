package io.github.aishwaryajayanth1820.tds.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;

/**
 * SQLite needs its parent directory to exist and a few per-connection pragmas
 * (architecture §6.4). Applied only when the JDBC URL is a SQLite file, so the
 * PostgreSQL profile is untouched.
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    static final String SQLITE_PREFIX = "jdbc:sqlite:";

    @Bean
    static BeanPostProcessor sqliteDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof HikariDataSource ds && ds.getJdbcUrl() != null && ds.getJdbcUrl().startsWith(SQLITE_PREFIX)) {
                    configureSqlite(ds);
                }
                return bean;
            }
        };
    }

    static void configureSqlite(HikariDataSource ds) {
        sqliteFile(ds.getJdbcUrl()).ifPresent(DataSourceConfig::createParentDirectory);
        ds.addDataSourceProperty("foreign_keys", "true");
        ds.addDataSourceProperty("journal_mode", "WAL");
        ds.addDataSourceProperty("busy_timeout", "5000");
        ds.addDataSourceProperty("synchronous", "NORMAL");
    }

    /** The database file of a {@code jdbc:sqlite:} URL, or empty for in-memory databases. */
    static java.util.Optional<Path> sqliteFile(String url) {
        String spec = url.substring(SQLITE_PREFIX.length());
        int query = spec.indexOf('?');
        if (query >= 0) {
            spec = spec.substring(0, query);
        }
        if (spec.isBlank() || spec.startsWith(":memory:") || spec.startsWith("file:")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Path.of(spec));
    }

    private static void createParentDirectory(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create database directory " + parent, e);
        }
    }
}
