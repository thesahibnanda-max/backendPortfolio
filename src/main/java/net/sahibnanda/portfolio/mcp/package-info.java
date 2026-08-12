/**
 * This app's own self-referential MCP client: connects back into this same
 * app's MCP server (see
 * {@link net.sahibnanda.portfolio.config.McpServerConfig}) at a base URL
 * resolved fresh from each incoming request, and translates between MCP's tool
 * schema and Groq's OpenAI-compatible tool-calling schema.
 */
package net.sahibnanda.portfolio.mcp;
