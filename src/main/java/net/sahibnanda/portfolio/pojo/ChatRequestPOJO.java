package net.sahibnanda.portfolio.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@Jacksonized
@Getter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class ChatRequestPOJO extends RequestPOJO {

  /**
   * The identifier of an existing chat. Used by {@code getChatById},
   * {@code updateTitle}, and {@code userPrompt}.
   */
  private final String chatId;

  /**
   * The chat's title. Used by {@code createChat} (the new chat's title) and
   * {@code updateTitle} (the new title).
   */
  private final String chatTitle;

  /** The user's message. Used only by {@code userPrompt}. */
  private final String message;
}
