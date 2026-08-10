package net.sahibnanda.portfolio.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * A wire type sent as the payload of the terminal SSE {@code done} event.
 */
@Builder
@Jacksonized
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public final class ChatStreamDonePOJO {

  /** The full, concatenated assistant answer. */
  private final String message;

  /** When the message was persisted. */
  private final Instant timestamp;
}
