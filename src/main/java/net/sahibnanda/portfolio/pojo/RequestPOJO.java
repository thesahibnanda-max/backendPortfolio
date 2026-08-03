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

import java.time.LocalDateTime;
import java.util.Map;

@SuperBuilder(toBuilder = true)
@Jacksonized
@Getter
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class RequestPOJO {
  /** When this request was made. */
  @JsonIgnore
  private final LocalDateTime timestamp;

  /** Additional request headers. Never null; empty when none are set. */
  @Builder.Default
  @JsonIgnore
  private final Map<String, String> headers = Map.of();
}
