package net.sahibnanda.portfolio.enums;

import lombok.Getter;

/**
 * The chat lifecycle event a chat observer DTO represents.
 */
@Getter
public enum ChatObserverStatus {

  /** A new chat was created. */
  CHAT_CREATED,

  /** A chat's messages were saved. */
  CHAT_MESSAGE_SAVED,

  /** A chat was deleted. */
  CHAT_DELETED,

  /** A chat's title was updated. */
  CHAT_TITLE_UPDATED
}
