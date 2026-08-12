package net.sahibnanda.portfolio.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArchitectureTypeTest {

  @Test
  void fromWireValueParsesBothKnownValuesCaseInsensitively() {
    assertThat(ArchitectureType.fromWireValue("mcp"))
        .isEqualTo(ArchitectureType.MCP);
    assertThat(ArchitectureType.fromWireValue("MCP"))
        .isEqualTo(ArchitectureType.MCP);
    assertThat(ArchitectureType.fromWireValue("orchestrator-worker"))
        .isEqualTo(ArchitectureType.ORCHESTRATOR_WORKER);
    assertThat(ArchitectureType.fromWireValue("Orchestrator-Worker"))
        .isEqualTo(ArchitectureType.ORCHESTRATOR_WORKER);
  }

  @Test
  void fromWireValueDefaultsToOrchestratorWorkerWhenNullOrBlank() {
    assertThat(ArchitectureType.fromWireValue(null))
        .isEqualTo(ArchitectureType.ORCHESTRATOR_WORKER);
    assertThat(ArchitectureType.fromWireValue("   "))
        .isEqualTo(ArchitectureType.ORCHESTRATOR_WORKER);
  }

  @Test
  void fromWireValueThrowsForAnUnknownValue() {
    assertThatThrownBy(() -> ArchitectureType.fromWireValue("bogus"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bogus");
  }

  @Test
  void getWireValueReturnsTheExpectedStringForEachConstant() {
    assertThat(ArchitectureType.MCP.getWireValue()).isEqualTo("mcp");
    assertThat(ArchitectureType.ORCHESTRATOR_WORKER.getWireValue())
        .isEqualTo("orchestrator-worker");
  }
}
