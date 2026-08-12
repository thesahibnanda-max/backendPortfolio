package net.sahibnanda.portfolio.config;

import net.sahibnanda.portfolio.services.DetailsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link DetailsService}'s {@code @Tool}-annotated methods as the
 * tool source Spring AI's MCP server (see {@code spring.ai.mcp.server.*} in
 * application.yml) exposes to MCP clients -- without this bean, the
 * {@code @Tool} annotations on {@link DetailsService} are inert: nothing ever
 * registers them with the server.
 */
@Configuration(proxyBeanMethods = false)
public final class McpServerConfig {

  /**
   * Exposes every {@code @Tool}-annotated {@link DetailsService} method as an
   * MCP tool.
   *
   * @param detailsService the tool-annotated bean to expose
   * @return the tool callback provider Spring AI's MCP server autoconfigures
   *         itself from
   */
  @Bean
  public ToolCallbackProvider detailsServiceToolCallbacks(
      final DetailsService detailsService) {
    return MethodToolCallbackProvider.builder().toolObjects(detailsService)
        .build();
  }
}
