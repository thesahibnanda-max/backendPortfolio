package net.sahibnanda.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import net.sahibnanda.portfolio.enums.UserObserverStatus;

/**
 * A user lifecycle event published to Kafka by the user repository observer.
 * Fields not relevant to a given {@link #status} are left {@code null} rather
 * than re-fetched, to avoid an extra database round trip on every user write.
 * The password hash is deliberately never included.
 */
@SuperBuilder
@Jacksonized
@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class UserObserverDTO {

  /** The user's username. */
  private final String username;

  /** When the user was created, if known for this event. */
  private final LocalDateTime createdAt;

  /** The lifecycle event this DTO represents. */
  private final UserObserverStatus status;
}
