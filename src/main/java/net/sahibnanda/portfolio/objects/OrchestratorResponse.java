package net.sahibnanda.portfolio.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import net.sahibnanda.portfolio.enums.ContextType;

/**
 * The Orchestrator AI's strict JSON output: which knowledge domains are
 * required to answer the user's question, and why.
 */
@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrchestratorResponse {

  /** Knowledge domains required to answer accurately. */
  private List<ContextType> requiredContexts;

  /** Short explanation of why these domains were selected. */
  private String reason;
}
