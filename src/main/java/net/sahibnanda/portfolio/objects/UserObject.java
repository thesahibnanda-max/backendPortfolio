package net.sahibnanda.portfolio.objects;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * A user's public-facing details -- everything in
 * {@link net.sahibnanda.portfolio.entity.UserEntity} except the password hash.
 */
@Builder
@Data
public final class UserObject {

  /** The user's username. */
  private final String username;

  /** When the user account was created. */
  private final LocalDateTime createdAt;
}
