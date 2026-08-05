package net.sahibnanda.portfolio.enums;

import lombok.Getter;

/**
 * The chat lifecycle event a chat observer DTO represents.
 */
@Getter
public enum ChatObserverStatus {

  /** A new chat was created. */
  CHAT_CREATED,

  /** A user-authored message was saved to a chat. */
  CHAT_MESSAGE_SAVED_USER,

  /** An assistant-authored message was saved to a chat. */
  CHAT_MESSAGE_SAVED_ASSISTANT,

  /** A chat was deleted. */
  CHAT_DELETED,

  /** A chat's title was updated. */
  CHAT_TITLE_UPDATED
}
