package net.sahibnanda.portfolio.entity;

import java.time.LocalDateTime;
import java.util.Objects;

public record UserEntity(String username, String passwordHash,
    LocalDateTime createdAt) {

  /** Validates that no component of the user is null. */
  public UserEntity {
    Objects.requireNonNull(username, "username must not be null");
    Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }
}
