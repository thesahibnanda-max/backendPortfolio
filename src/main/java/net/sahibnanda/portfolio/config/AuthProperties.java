package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for auth-token encryption.
 *
 * @param secretKey the passphrase auth tokens are encrypted/decrypted with --
 *        hashed down to an AES-256 key, so any string length works. Must be
 *        overridden via {@code AUTH_SECRET_KEY} outside local development;
 *        unlike this app's other secrets, this one directly controls whether
 *        tokens can be forged.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(String secretKey) {
}
