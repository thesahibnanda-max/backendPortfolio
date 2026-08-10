package net.sahibnanda.portfolio.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * A wire type sent as the payload of an SSE {@code token} event.
 */
@Builder
@Jacksonized
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public final class ChatStreamTokenPOJO {

  /** The incremental text chunk. */
  private final String content;
}
