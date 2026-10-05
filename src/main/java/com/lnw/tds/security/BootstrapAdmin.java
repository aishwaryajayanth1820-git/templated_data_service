package com.lnw.tds.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.lnw.tds.config.TdsProperties;

/**
 * Creates the first admin when the user table is empty (ADR-0008). The password comes from
 * {@code TDS_ADMIN_PASSWORD} or is generated and logged once; it must be changed at first login.
 */
@Component
@Order(10)
class BootstrapAdmin implements ApplicationRunner {

    static final String PASSWORD_PROPERTY = "TDS_ADMIN_PASSWORD";
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AccountService accounts;
    private final TdsProperties properties;
    private final Environment environment;

    BootstrapAdmin(AccountService accounts, TdsProperties properties, Environment environment) {
        this.accounts = accounts;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.userCount() > 0) {
            return;
        }
        String username = properties.security().bootstrapAdmin();
        String password = environment.getProperty(PASSWORD_PROPERTY);
        boolean generated = password == null || password.isBlank();
        if (generated) {
            password = generatePassword();
        }
        accounts.createUser(username, "Administrator", password, Set.of(Roles.ADMIN), true);
        if (generated) {
            log.warn("Created initial admin user '{}' with generated password: {}  (change it at first login)", username, password);
        } else {
            log.info("Created initial admin user '{}' from {} (change it at first login)", username, PASSWORD_PROPERTY);
        }
    }

    private static String generatePassword() {
        byte[] bytes = new byte[15];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
