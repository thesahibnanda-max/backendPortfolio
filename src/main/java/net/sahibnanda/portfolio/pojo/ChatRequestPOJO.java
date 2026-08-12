package net.sahibnanda.portfolio.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import net.sahibnanda.portfolio.enums.ArchitectureType;

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

  /**
   * Which AI pipeline should answer this message. Defaults to
   * {@link ArchitectureType#ORCHESTRATOR_WORKER} when omitted. Used only by
   * {@code userPrompt}/{@code userPromptStream}.
   */
  @Builder.Default
  private final ArchitectureType architecture =
      ArchitectureType.ORCHESTRATOR_WORKER;

  /**
   * This app's own loopback base URL, built by {@code Controller} from its own
   * configured port -- never supplied by the client's JSON body, and never
   * derived from the caller's request. Used only when {@link #architecture} is
   * {@link ArchitectureType#MCP}.
   */
  @JsonIgnore
  private final String mcpBaseUrl;
}
