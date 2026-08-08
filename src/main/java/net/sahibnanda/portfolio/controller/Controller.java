package net.sahibnanda.portfolio.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.sahibnanda.portfolio.core.Core;
import net.sahibnanda.portfolio.middleware.SessionHeaderResolver;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ResponsePOJO;
import net.sahibnanda.portfolio.pojo.SearchRequestPOJO;
import net.sahibnanda.portfolio.pojo.UserGateRequestPOJO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * Exposes every {@link Core} operation over HTTP.
 */
@RestController
public class Controller {

  /** The header non-Gate requests carry their auth token in. */
  private static final String X_AUTH_TOKEN = "X-Auth-Token";

  /** Wires every operation this controller exposes. */
  private final Core core;

  /**
   * Resolves the anonymous session id header for every request -- the
   * middleware layer sitting between this controller and {@link Core}.
   */
  private final SessionHeaderResolver sessionHeaderResolver;

  /**
   * Constructs a new controller.
   *
   * @param coreService wires every operation this controller exposes
   * @param sessionHeaderResolverParam resolves the anonymous session id header
   *        for every request
   */
  public Controller(final Core coreService,
      final SessionHeaderResolver sessionHeaderResolverParam) {
    this.core = Objects.requireNonNull(coreService, "core is null");
    this.sessionHeaderResolver = Objects.requireNonNull(
        sessionHeaderResolverParam, "sessionHeaderResolver is null");
  }

  /**
   * Registers a new user.
   *
   * @param request the requested username and password
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the created user's auth token, or a failure response
   */
  @PostMapping("/signup")
  public ResponseEntity<ResponsePOJO> signUp(
      @RequestBody final UserGateRequestPOJO request,
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.signUp(request));
  }

  /**
   * Authenticates a user.
   *
   * @param request the username and password to authenticate
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the authenticated user's auth token, or a failure response
   */
  @PostMapping("/login")
  public ResponseEntity<ResponsePOJO> login(
      @RequestBody final UserGateRequestPOJO request,
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.login(request));
  }

  /**
   * Creates a new chat for the requesting caller. Authenticated callers
   * (carrying a valid {@code X-Auth-Token}) get a durable chat owned by their
   * username; anonymous callers get an ephemeral chat owned by their session id
   * header instead.
   *
   * @param authToken the caller's {@code X-Auth-Token}
   * @param request the new chat's title
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return every chat owned by the user, or the newly created anonymous chat,
   *         or a failure response
   */
  @PostMapping("/chats")
  public ResponseEntity<ResponsePOJO> createChat(
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      @RequestBody final ChatRequestPOJO request,
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    String sessionId =
        sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(
        core.createChat(enrich(request, authToken, sessionId)));
  }

  /**
   * Lists every chat owned by the requesting user.
   *
   * @param authToken the caller's {@code X-Auth-Token}
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return every chat owned by the user, or a failure response
   */
  @GetMapping("/chats")
  public ResponseEntity<ResponsePOJO> allChats(
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    String sessionId =
        sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    ChatRequestPOJO request =
        enrich(ChatRequestPOJO.builder().build(), authToken, sessionId);
    return toResponseEntity(core.allChats(request));
  }

  /**
   * Fetches a single chat owned by the requesting caller.
   *
   * @param chatId the identifier of the chat to fetch
   * @param authToken the caller's {@code X-Auth-Token}
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the requested chat, or a failure response
   */
  @GetMapping("/chats/{chatId}")
  public ResponseEntity<ResponsePOJO> getChatById(
      @PathVariable final String chatId,
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    String sessionId =
        sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    ChatRequestPOJO request = enrich(
        ChatRequestPOJO.builder().chatId(chatId).build(), authToken, sessionId);
    return toResponseEntity(core.getChatById(request));
  }

  /**
   * Renames a chat owned by the requesting user.
   *
   * @param chatId the identifier of the chat to rename
   * @param authToken the caller's {@code X-Auth-Token}
   * @param request the new title
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the renamed chat, or a failure response
   */
  @PatchMapping("/chats/{chatId}")
  public ResponseEntity<ResponsePOJO> updateTitle(
      @PathVariable final String chatId,
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      @RequestBody final ChatRequestPOJO request,
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    String sessionId =
        sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    ChatRequestPOJO merged = enrich(request.toBuilder().chatId(chatId).build(),
        authToken, sessionId);
    return toResponseEntity(core.updateTitle(merged));
  }

  /**
   * Answers the caller's latest message in a chat.
   *
   * @param chatId the identifier of the chat to send the message to
   * @param authToken the caller's {@code X-Auth-Token}
   * @param request the message to send
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the chat, including the new reply, or a failure response
   */
  @PostMapping("/chats/{chatId}/messages")
  public ResponseEntity<ResponsePOJO> userPrompt(
      @PathVariable final String chatId,
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      @RequestBody final ChatRequestPOJO request,
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    String sessionId =
        sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    ChatRequestPOJO merged = enrich(request.toBuilder().chatId(chatId).build(),
        authToken, sessionId);
    return toResponseEntity(core.userPrompt(merged));
  }

  /**
   * Searches the requesting user's chats.
   *
   * @param authToken the caller's {@code X-Auth-Token}
   * @param request the search text
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return matching chats and their relevance scores, or a failure response
   */
  @PostMapping("/chats/search")
  public ResponseEntity<ResponsePOJO> searchChat(
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      @RequestBody final SearchRequestPOJO request,
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    String sessionId =
        sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(
        core.searchChat(enrich(request, authToken, sessionId)));
  }

  /**
   * Checks database connectivity.
   *
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return 200 if the database is reachable, or a failure response
   */
  @GetMapping("/health")
  public ResponseEntity<ResponsePOJO> health(
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.health());
  }

  /**
   * Returns the portfolio owner's professional links: LeetCode, Codeforces, and
   * GitHub profile links, the resume link, and profile photo link(s).
   *
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the professional details, or a failure response
   */
  @GetMapping("/details/professional")
  public ResponseEntity<ResponsePOJO> professionalDetails(
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.professionalDetails());
  }

  /**
   * Returns details for every configured LeetCode account.
   *
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the LeetCode details, or a failure response
   */
  @GetMapping("/details/leetcode")
  public ResponseEntity<ResponsePOJO> leetcodeDetails(
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.leetcodeDetails());
  }

  /**
   * Returns details for every configured Codeforces account.
   *
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the Codeforces details, or a failure response
   */
  @GetMapping("/details/codeforces")
  public ResponseEntity<ResponsePOJO> codeforcesDetails(
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.codeforcesDetails());
  }

  /**
   * Returns details for every configured GitHub account.
   *
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the GitHub details, or a failure response
   */
  @GetMapping("/details/github")
  public ResponseEntity<ResponsePOJO> githubDetails(
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.githubDetails());
  }

  /**
   * Returns the portfolio owner's personality profile.
   *
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the personality details, or a failure response
   */
  @GetMapping("/details/personality")
  public ResponseEntity<ResponsePOJO> personalityDetails(
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.personalityDetails());
  }

  /**
   * Returns the portfolio owner's profile.
   *
   * @param servletRequest the incoming request, checked for a session id header
   * @param servletResponse the outgoing response, given a session id header if
   *        one wasn't already present
   * @return the profile details, or a failure response
   */
  @GetMapping("/details/profile")
  public ResponseEntity<ResponsePOJO> profileDetails(
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse) {
    sessionHeaderResolver.resolveSessionId(servletRequest, servletResponse);
    return toResponseEntity(core.profileDetails());
  }

  private static ChatRequestPOJO enrich(final ChatRequestPOJO request,
      final String authToken, final String sessionId) {
    Map<String, String> headers =
        authToken == null ? Map.of() : Map.of(X_AUTH_TOKEN, authToken);
    return request.toBuilder().headers(headers).sessionId(sessionId).build();
  }

  private static SearchRequestPOJO enrich(final SearchRequestPOJO request,
      final String authToken, final String sessionId) {
    Map<String, String> headers =
        authToken == null ? Map.of() : Map.of(X_AUTH_TOKEN, authToken);
    return request.toBuilder().headers(headers).sessionId(sessionId).build();
  }

  private static ResponseEntity<ResponsePOJO> toResponseEntity(
      final ResponsePOJO response) {
    HttpHeaders httpHeaders = new HttpHeaders();
    response.getHeaders().forEach(httpHeaders::add);
    return ResponseEntity.status(response.getHttpStatusCode())
        .headers(httpHeaders).body(response);
  }
}
