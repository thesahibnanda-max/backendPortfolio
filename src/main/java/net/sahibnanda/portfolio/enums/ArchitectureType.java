package net.sahibnanda.portfolio.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import net.sahibnanda.portfolio.utils.StringUtils;

/**
 * Which AI pipeline answers a chat message: the default two-stage
 * Orchestrator/Worker pipeline
 * ({@link net.sahibnanda.portfolio.services.OrchestratorService} +
 * {@link net.sahibnanda.portfolio.services.WorkerService}), or the single
 * self-directed MCP AI ({@link net.sahibnanda.portfolio.services.McpAiService})
 * that decides for itself, via Groq's OpenAI-compatible {@code tools} field,
 * which MCP tools (if any) to call.
 */
public enum ArchitectureType {

  /** The default: a routing AI selects contexts, a separate AI answers. */
  ORCHESTRATOR_WORKER("orchestrator-worker"),

  /** A single AI answers, calling MCP tools itself as it sees fit. */
  MCP("mcp");

  /** The wire value clients send/receive for this architecture. */
  private final String wireValue;

  ArchitectureType(final String architectureWireValue) {
    this.wireValue = architectureWireValue;
  }

  /**
   * Returns the wire value serialized for this architecture.
   *
   * @return the wire value
   */
  @JsonValue
  public String getWireValue() {
    return wireValue;
  }

  /**
   * Resolves a wire value into an {@code ArchitectureType}, defaulting to
   * {@link #ORCHESTRATOR_WORKER} when {@code value} is null or blank.
   *
   * @param value the wire value to resolve
   * @return the matching architecture, or {@link #ORCHESTRATOR_WORKER} if
   *         {@code value} is null or blank
   * @throws IllegalArgumentException if {@code value} is non-blank but matches
   *         no known architecture
   */
  @JsonCreator
  public static ArchitectureType fromWireValue(final String value) {
    if (StringUtils.isEmpty(value)) {
      return ORCHESTRATOR_WORKER;
    }
    for (ArchitectureType type : values()) {
      if (type.wireValue.equalsIgnoreCase(value.trim())) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown architecture: " + value);
  }
}
