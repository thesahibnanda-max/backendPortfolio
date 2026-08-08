package net.sahibnanda.portfolio.middleware;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Resolves the anonymous session id for a request from the {@code
 * X-Session-Id} header, called by {@code Controller} at the top of every
 * endpoint -- the middleware layer sitting between {@code Controller} and
 * {@code Core}. If the incoming request already carries a valid session id
 * header, it's reused unchanged; if not, a new one is generated and set on the
 * response for the caller to persist and send back on future requests.
 *
 * <p>
 * A header (mirroring how {@code X-Auth-Token} already works) is used instead
 * of a cookie because the frontend and this API are deployed on different
 * registrable domains -- a cross-site cookie is a third-party cookie, which
 * Safari and strict-mode Firefox block by default regardless of {@code
 * SameSite}/{@code Secure} configuration. A header the client explicitly stores
 * (e.g. in {@code localStorage}) and replays isn't subject to that restriction.
 */
@Component
public final class SessionHeaderResolver {

  /** The header the resolved session id is read from and written to. */
  public static final String X_SESSION_ID = "X-Session-Id";

  /**
   * Reads {@value #X_SESSION_ID} from the request, reusing it if present and
   * valid; otherwise generates a new one and sets it on the response.
   *
   * @param request the incoming request, checked for an existing session id
   *        header
   * @param response the outgoing response, given the {@value #X_SESSION_ID}
   *        header when a new session id is generated
   * @return the resolved session id, never null or blank
   */
  public String resolveSessionId(final HttpServletRequest request,
      final HttpServletResponse response) {
    String existing = request.getHeader(X_SESSION_ID);
    if (StringUtils.isValidUlid(existing)) {
      return existing;
    }

    String sessionId = StringUtils.generateUlid();
    response.setHeader(X_SESSION_ID, sessionId);
    return sessionId;
  }
}
