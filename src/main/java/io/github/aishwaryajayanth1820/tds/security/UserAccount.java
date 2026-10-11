package io.github.aishwaryajayanth1820.tds.security;

import java.time.Instant;

public record UserAccount(long id, String username, String passwordHash, String displayName,
                          boolean enabled, boolean mustChangePassword, Instant createdAt, Instant updatedAt) {}
