package net.sahibnanda.portfolio.middleware;

import jakarta.servlet.http.HttpServletRequest;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller's IP address from the {@code X-Forwarded-For} header set
 * by the Caddy reverse proxy this application runs behind in production. Caddy
 * always sets/overwrites this header itself before forwarding to 127.0.0.1:8081
 * -&gt; the container's 8080, so it cannot be spoofed by a client as long as
 * Caddy sits in front of every request. If this app is ever exposed directly
 * without Caddy (or an equivalent trusted proxy) in front, this becomes a
 * trivial spoofing vector and this resolver must be revisited.
 */
@Component
public final class ClientIpResolver {

  /** The header Caddy sets with the real client IP. */
  public static final String X_FORWARDED_FOR = "X-Forwarded-For";

  /**
   * Resolves the caller's IP address for the given request.
   *
   * @param request the incoming request
   * @return the first IP in {@value #X_FORWARDED_FOR}, or
   *         {@code request.getRemoteAddr()} if the header is absent/blank
   */
  public String resolveClientIp(final HttpServletRequest request) {
    String header = request.getHeader(X_FORWARDED_FOR);
    if (StringUtils.isEmpty(header)) {
      return request.getRemoteAddr();
    }
    String firstIp = header.split(",")[0].trim();
    return StringUtils.isEmpty(firstIp) ? request.getRemoteAddr() : firstIp;
  }
}
