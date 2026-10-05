package com.lnw.tds.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lnw.tds.jdbc.DbVendorResolver;
import com.lnw.tds.jdbc.JdbcTime;

@Repository
class UserRepository {

    private final JdbcClient jdbc;
    private final DbVendorResolver vendor;

    UserRepository(JdbcClient jdbc, DbVendorResolver vendor) {
        this.jdbc = jdbc;
        this.vendor = vendor;
    }

    Optional<UserAccount> findByUsername(String username) {
        return jdbc.sql("SELECT * FROM tds_user WHERE username = :u").param("u", username).query(UserRepository::map).optional();
    }

    List<UserAccount> findAll() {
        return jdbc.sql("SELECT * FROM tds_user ORDER BY username").query(UserRepository::map).list();
    }

    long count() {
        return jdbc.sql("SELECT COUNT(*) FROM tds_user").query(Long.class).single();
    }

    long insert(String username, String passwordHash, String displayName, boolean mustChangePassword) {
        return jdbc.sql("""
                INSERT INTO tds_user (username, password_hash, display_name, must_change_password)
                VALUES (:u, :h, :d, :m) RETURNING id""")
                .param("u", username).param("h", passwordHash).param("d", displayName).param("m", mustChangePassword)
                .query(Long.class).single();
    }

    void updatePassword(long id, String passwordHash, boolean mustChangePassword) {
        jdbc.sql("UPDATE tds_user SET password_hash = :h, must_change_password = :m, updated_at = " + vendor.current().nowSql()
                        + " WHERE id = :id")
                .param("h", passwordHash).param("m", mustChangePassword).param("id", id).update();
    }

    Set<String> roleNames(long userId) {
        return new LinkedHashSet<>(jdbc.sql("""
                SELECT r.name FROM tds_role r JOIN tds_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = :id ORDER BY r.name""").param("id", userId).query(String.class).list());
    }

    void updateProfile(long id, String displayName, boolean enabled) {
        jdbc.sql("UPDATE tds_user SET display_name = :d, enabled = :e, updated_at = " + vendor.current().nowSql() + " WHERE id = :id")
                .param("d", displayName).param("e", enabled).param("id", id).update();
    }

    void clearRoles(long userId) {
        jdbc.sql("DELETE FROM tds_user_role WHERE user_id = :u").param("u", userId).update();
    }

    void addRole(long userId, long roleId) {
        jdbc.sql("INSERT INTO tds_user_role (user_id, role_id) VALUES (:u, :r)").param("u", userId).param("r", roleId).update();
    }

    private static UserAccount map(ResultSet rs, int row) throws SQLException {
        return new UserAccount(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getBoolean("enabled"), rs.getBoolean("must_change_password"),
                JdbcTime.toInstant(rs.getObject("created_at")), JdbcTime.toInstant(rs.getObject("updated_at")));
    }
}
