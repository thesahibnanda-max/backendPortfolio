package net.sahibnanda.portfolio.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.http.HttpStatusCode;

import java.time.LocalDateTime;
import java.util.Map;

@SuperBuilder
@Jacksonized
@Getter
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class ResponsePOJO {

  /** The HTTP status this response corresponds to. */
  @JsonIgnore
  private final HttpStatusCode httpStatusCode;

  /** When this response was generated. */
  @JsonIgnore
  private final LocalDateTime timestamp;

  /** Additional response headers. Never null; empty when none are set. */
  @JsonIgnore
  @Builder.Default
  private final Map<String, String> headers = Map.of();
}
