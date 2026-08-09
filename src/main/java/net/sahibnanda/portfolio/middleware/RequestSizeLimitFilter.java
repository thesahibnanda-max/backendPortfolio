package net.sahibnanda.portfolio.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests whose declared {@code Content-Length} exceeds
 * {@value #MAX_BODY_BYTES} before any body parsing happens -- a cheap backstop
 * against megabyte/gigabyte-scale garbage payloads, well above the char-length
 * caps in {@code ChatLimitsProperties} (which only run after successful JSON
 * deserialization). Does not catch chunked-encoding requests that omit
 * {@code Content-Length} -- {@code server.tomcat.max-swallow-size} and the
 * Caddy-level {@code request_body max_size} (VM config, not this repo) are the
 * remaining backstops for that case.
 */
@Component
public final class RequestSizeLimitFilter extends OncePerRequestFilter {

  /**
   * Generous headroom over the largest legitimate JSON body this API expects.
   */
  private static final long MAX_BODY_BYTES = 262_144; // 256 KiB

  @Override
  protected void doFilterInternal(final HttpServletRequest request,
      final HttpServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
          "Request body too large.");
      return;
    }
    chain.doFilter(request, response);
  }
}
