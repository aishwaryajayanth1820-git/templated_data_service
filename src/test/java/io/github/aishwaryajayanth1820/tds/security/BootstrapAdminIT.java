package io.github.aishwaryajayanth1820.tds.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.aishwaryajayanth1820.tds.IntegrationTestBase;

class BootstrapAdminIT extends IntegrationTestBase {

    @Autowired
    UserRepository users;

    @Autowired
    BootstrapAdmin bootstrap;

    @Autowired
    PasswordEncoder encoder;

    @Test
    void createsAdminWithConfiguredPasswordAndForcedChange() {
        UserAccount admin = users.findByUsername("admin").orElseThrow();

        assertThat(admin.mustChangePassword()).isTrue();
        assertThat(admin.enabled()).isTrue();
        assertThat(encoder.matches(ADMIN_PASSWORD, admin.passwordHash())).isTrue();
        assertThat(users.roleNames(admin.id())).containsExactly("admin");
    }

    @Test
    void doesNothingWhenUsersExist() {
        long before = users.count();

        bootstrap.run(new DefaultApplicationArguments());

        assertThat(users.count()).isEqualTo(before);
    }
}
