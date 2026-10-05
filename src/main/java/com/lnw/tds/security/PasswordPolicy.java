package com.lnw.tds.security;

import java.util.Optional;

/** v1 password rules (application design §4). Returns the reason a password is rejected, if any. */
final class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 128;

    private PasswordPolicy() {}

    static Optional<String> check(String username, String candidate) {
        if (candidate == null || candidate.length() < MIN_LENGTH) {
            return Optional.of("Use at least " + MIN_LENGTH + " characters");
        }
        if (candidate.length() > MAX_LENGTH) {
            return Optional.of("Use at most " + MAX_LENGTH + " characters");
        }
        if (candidate.equalsIgnoreCase(username)) {
            return Optional.of("The password must not be the username");
        }
        return Optional.empty();
    }
}
