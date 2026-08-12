package net.sahibnanda.portfolio.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.sahibnanda.portfolio.constants.MCPToolNames;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Exercises {@link McpToolClient} against this app's own, really-running MCP
 * server (a real embedded Tomcat on a random port, via {@code webEnvironment
 * = RANDOM_PORT}) -- not disabled, unlike the Groq-calling integration tests
 * elsewhere in this suite, because nothing here spends real API budget: this
 * only talks the MCP protocol to this same JVM's own server, which in turn
 * calls the same real, free LeetCode/GitHub/Codeforces/profile APIs
 * {@link net.sahibnanda.portfolio.services.DetailsServiceTest} already calls
 * unconditionally.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpToolClientIntegrationTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private McpToolClient mcpToolClient;

  @LocalServerPort
  private int port;

  @Test
  void listToolsAsGroqToolsReturnsExactlyTheSixDetailsServiceTools() {
    try (McpToolClient.Session session = mcpToolClient.open(baseUrl())) {
      List<GroqCallRequest.Tool> tools = session.listToolsAsGroqTools();

      List<String> names =
          tools.stream().map(tool -> tool.getFunction().getName()).toList();
      assertThat(names).containsExactlyInAnyOrder(
          MCPToolNames.PROFESSIONAL_DETAILS, MCPToolNames.LEETCODE_DETAILS,
          MCPToolNames.CODEFORCES_DETAILS, MCPToolNames.GITHUB_DETAILS,
          MCPToolNames.PROFILE_DETAILS, MCPToolNames.PERSONALITY_DETAILS);
    }
  }

  @Test
  void callToolReturnsRealProfessionalDetailsAsNonBlankJson() {
    try (McpToolClient.Session session = mcpToolClient.open(baseUrl())) {
      String result = session.callTool(MCPToolNames.PROFESSIONAL_DETAILS, "{}");

      assertThat(result).isNotBlank();
      assertThat(result).containsIgnoringCase("resumeLink");
    }
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }
}
