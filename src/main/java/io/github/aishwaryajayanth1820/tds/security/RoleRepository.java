package io.github.aishwaryajayanth1820.tds.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RoleRepository {

    private final JdbcClient jdbc;

    RoleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Role> findAll() {
        return jdbc.sql("SELECT * FROM tds_role ORDER BY name").query(RoleRepository::map).list();
    }

    Optional<Role> findByName(String name) {
        return jdbc.sql("SELECT * FROM tds_role WHERE name = :n").param("n", name).query(RoleRepository::map).optional();
    }

    void insert(String name, String description) {
        jdbc.sql("INSERT INTO tds_role (name, description) VALUES (:n, :d)").param("n", name).param("d", description).update();
    }

    void updateDescription(long id, String description) {
        jdbc.sql("UPDATE tds_role SET description = :d WHERE id = :id").param("d", description).param("id", id).update();
    }

    void delete(long id) {
        jdbc.sql("DELETE FROM tds_role WHERE id = :id").param("id", id).update();
    }

    long countUsers(long roleId) {
        return jdbc.sql("SELECT COUNT(*) FROM tds_user_role WHERE role_id = :id").param("id", roleId).query(Long.class).single();
    }

    private static Role map(ResultSet rs, int row) throws SQLException {
        return new Role(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getBoolean("builtin"));
    }
}
