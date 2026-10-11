package io.github.aishwaryajayanth1820.tds.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

/** Detects the database engine once from the connection metadata. */
@Component
public class DbVendorResolver {

    private final DataSource dataSource;
    private volatile DbVendor vendor;

    public DbVendorResolver(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DbVendor current() {
        DbVendor v = vendor;
        if (v == null) {
            try (Connection c = dataSource.getConnection()) {
                v = DbVendor.fromProductName(c.getMetaData().getDatabaseProductName());
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot determine database vendor", e);
            }
            vendor = v;
        }
        return v;
    }
}
