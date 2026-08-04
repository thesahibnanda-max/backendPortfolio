package net.sahibnanda.portfolio.enums;

import lombok.Getter;

/**
 * The user lifecycle event a user observer DTO represents.
 */
@Getter
public enum UserObserverStatus {

  /** A new user was created. */
  USER_CREATED,

  /** A user's password was updated. */
  USER_PASSWORD_UPDATED,

  /** A user was deleted. */
  USER_DELETED
}
