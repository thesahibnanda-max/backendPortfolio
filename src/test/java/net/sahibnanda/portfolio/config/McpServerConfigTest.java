package net.sahibnanda.portfolio.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import net.sahibnanda.portfolio.constants.MCPToolNames;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;

class McpServerConfigTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private ToolCallbackProvider detailsServiceToolCallbacks;

  @Test
  void exposesExactlyTheSixDetailsServiceTools() {
    ToolCallback[] callbacks = detailsServiceToolCallbacks.getToolCallbacks();

    List<String> names = Arrays.stream(callbacks)
        .map(callback -> callback.getToolDefinition().name()).toList();

    assertThat(names).containsExactlyInAnyOrder(
        MCPToolNames.PROFESSIONAL_DETAILS, MCPToolNames.LEETCODE_DETAILS,
        MCPToolNames.CODEFORCES_DETAILS, MCPToolNames.GITHUB_DETAILS,
        MCPToolNames.PROFILE_DETAILS, MCPToolNames.PERSONALITY_DETAILS);
  }
}
