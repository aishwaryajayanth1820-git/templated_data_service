package io.github.aishwaryajayanth1820.tds.ddl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import io.github.aishwaryajayanth1820.tds.ddl.MigrationPlan.PreCheck;
import io.github.aishwaryajayanth1820.tds.jdbc.DbVendor;
import io.github.aishwaryajayanth1820.tds.jdbc.DbVendorResolver;

/**
 * Runs a migration plan in one transaction on a dedicated connection. SQLite needs this because
 * {@code PRAGMA foreign_keys} cannot change inside a transaction (ADR-0013).
 */
@Component
public class MigrationExecutor {

    private final DataSource dataSource;
    private final JdbcClient jdbc;
    private final DbVendorResolver vendor;

    public MigrationExecutor(DataSource dataSource, JdbcClient jdbc, DbVendorResolver vendor) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.vendor = vendor;
    }

    public record PreCheckResult(String description, long violations, String error) {}

    public List<PreCheckResult> preCheck(MigrationPlan plan) {
        List<PreCheckResult> out = new ArrayList<>();
        for (PreCheck c : plan.preChecks()) {
            try {
                long n = jdbc.sql(c.countSql()).query(Long.class).single();
                out.add(new PreCheckResult(c.description(), n, null));
            } catch (RuntimeException e) {
                out.add(new PreCheckResult(c.description(), 0, "Could not pre-check: " + rootMessage(e)));
            }
        }
        return out;
    }

    /** Thrown when the database rejects a statement; the transaction has been rolled back. */
    public static class MigrationFailedException extends RuntimeException {
        public MigrationFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Executes {@code statements} and then {@code sameTransaction} (e.g. catalog bookkeeping) atomically.
     * On SQLite, foreign keys are off during a rebuild and {@code foreign_key_check} must come back empty.
     */
    public void apply(List<String> statements, boolean rebuild, Consumer<JdbcClient> sameTransaction) {
        boolean sqlite = vendor.current() == DbVendor.SQLITE;
        try (Connection c = dataSource.getConnection()) {
            boolean fkOff = sqlite && rebuild;
            try {
                if (fkOff) {
                    exec(c, "PRAGMA foreign_keys = OFF");
                }
                c.setAutoCommit(false);
                try {
                    for (String sql : statements) {
                        exec(c, sql);
                    }
                    if (fkOff) {
                        assertForeignKeys(c);
                    }
                    sameTransaction.accept(JdbcClient.create(new SingleConnectionDataSource(c, true)));
                    c.commit();
                } catch (SQLException | RuntimeException e) {
                    c.rollback();
                    throw new MigrationFailedException(rootMessage(e), e);
                }
            } finally {
                c.setAutoCommit(true);
                if (fkOff) {
                    exec(c, "PRAGMA foreign_keys = ON");
                }
            }
        } catch (SQLException e) {
            throw new MigrationFailedException(rootMessage(e), e);
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static void assertForeignKeys(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA foreign_key_check")) {
            if (rs.next()) {
                throw new SQLException("Foreign key check failed: table " + rs.getString(1) + " row " + rs.getLong(2)
                        + " references a missing row in " + rs.getString(3));
            }
        }
    }

    static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }
}
