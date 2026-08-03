package net.sahibnanda.portfolio.utils;

import lombok.experimental.UtilityClass;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Password hashing and verification, backed by BCrypt. */
@UtilityClass
public final class HashUtils {

  /** Shared BCrypt encoder used for both hashing and verification. */
  private static final BCryptPasswordEncoder ENCODER =
      new BCryptPasswordEncoder();

  /**
   * Hashes a raw password for storage.
   *
   * @param rawPassword the plain-text password to hash
   * @return the hashed password
   */
  public String hash(final String rawPassword) {
    return ENCODER.encode(rawPassword);
  }

  /**
   * Checks whether a raw password matches a previously hashed password.
   *
   * @param rawPassword the plain-text password to check
   * @param hashedPassword the previously hashed password to check against
   * @return {@code true} if the raw password matches the hash
   */
  public boolean matches(final String rawPassword,
      final String hashedPassword) {
    return ENCODER.matches(rawPassword, hashedPassword);
  }
}
