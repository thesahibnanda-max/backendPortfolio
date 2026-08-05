package net.sahibnanda.portfolio.core;

import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.AuthProperties;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.exception.CacheSetException;
import net.sahibnanda.portfolio.exception.ChatAccessDeniedException;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.exception.CodeforcesCallException;
import net.sahibnanda.portfolio.exception.DatabaseOperationException;
import net.sahibnanda.portfolio.exception.DuplicateUsernameException;
import net.sahibnanda.portfolio.exception.GitHubCallException;
import net.sahibnanda.portfolio.exception.GroqCallException;
import net.sahibnanda.portfolio.exception.InvalidCredentialsException;
import net.sahibnanda.portfolio.exception.InvalidEmailException;
import net.sahibnanda.portfolio.exception.InvalidPasswordException;
import net.sahibnanda.portfolio.exception.JsonExtractionException;
import net.sahibnanda.portfolio.exception.KafkaConsumerAlreadyStartedException;
import net.sahibnanda.portfolio.exception.KafkaOperationException;
import net.sahibnanda.portfolio.exception.LeetcodeCallException;
import net.sahibnanda.portfolio.exception.OpenSearchOperationException;
import net.sahibnanda.portfolio.exception.ProfileCallException;
import net.sahibnanda.portfolio.exception.RateLimitExceededException;
import net.sahibnanda.portfolio.exception.RepositoryException;
import net.sahibnanda.portfolio.exception.TokenException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.exception.ValkeyCacheException;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.objects.ChatSearchResult;
import net.sahibnanda.portfolio.objects.OrchestratorResponse;
import net.sahibnanda.portfolio.objects.UserObject;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.ErrorResponsePOJO;
import net.sahibnanda.portfolio.pojo.ListOfChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.RequestPOJO;
import net.sahibnanda.portfolio.pojo.ResponsePOJO;
import net.sahibnanda.portfolio.pojo.SearchRequestPOJO;
import net.sahibnanda.portfolio.pojo.SearchResponsePOJO;
import net.sahibnanda.portfolio.pojo.UserGateRequestPOJO;
import net.sahibnanda.portfolio.pojo.UserGateResponsePOJO;
import net.sahibnanda.portfolio.services.CronPingService;
import net.sahibnanda.portfolio.services.OrchestratorService;
import net.sahibnanda.portfolio.services.RateLimitService;
import net.sahibnanda.portfolio.services.SearchService;
import net.sahibnanda.portfolio.services.UserChatService;
import net.sahibnanda.portfolio.services.WorkerService;
import net.sahibnanda.portfolio.utils.StringUtils;
import net.sahibnanda.portfolio.utils.TokenUtils;
import net.sahibnanda.portfolio.utils.ValidatorUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wires the Gate (sign-up/login), Orchestrator, and Worker AI services into the
 * operations a future controller layer will expose.
 */
@Slf4j
@Service
public class Core {

  /** The response header the encrypted auth token is returned under. */
  private static final String X_AUTH_TOKEN = "X-Auth-Token";

  /** Shown for internal failures not safe to expose to the caller. */
  private static final String INTERNAL_ERROR_MESSAGE =
      "An unexpected error occurred. Please try again later.";

  /** Shown when a third-party dependency call fails. */
  private static final String UPSTREAM_ERROR_MESSAGE =
      "A downstream service is currently unavailable. Please try again "
          + "later.";

  /** API name used to key {@link #signUp}'s rate limit. */
  private static final String API_SIGN_UP = "signUp";

  /** API name used to key {@link #login}'s rate limit. */
  private static final String API_LOGIN = "login";

  /** API name used to key {@link #createChat}'s rate limit. */
  private static final String API_CREATE_CHAT = "createChat";

  /** API name used to key {@link #allChats}'s rate limit. */
  private static final String API_ALL_CHATS = "allChats";

  /** API name used to key {@link #getChatById}'s rate limit. */
  private static final String API_GET_CHAT_BY_ID = "getChatById";

  /** API name used to key {@link #updateTitle}'s rate limit. */
  private static final String API_UPDATE_TITLE = "updateTitle";

  /** API name used to key {@link #userPrompt}'s rate limit. */
  private static final String API_USER_PROMPT = "userPrompt";

  /** API name used to key {@link #searchChat}'s rate limit. */
  private static final String API_SEARCH_CHAT = "searchChat";

  /** Handles user sign-up and login against the users table. */
  private final UserChatService userChatService;

  /** Routes chat requests to the appropriate worker. */
  private final OrchestratorService orchestratorService;

  /** Executes chat requests against the configured LLM. */
  private final WorkerService workerService;

  /** Configuration for encrypting/decrypting auth tokens. */
  private final AuthProperties authProperties;

  /** Checks database connectivity for the health endpoint. */
  private final CronPingService cronPingService;

  /** Enforces per-API and per-username rate limits. */
  private final RateLimitService rateLimitService;

  /** Searches the requesting user's chats. */
  private final SearchService searchService;

  /**
   * How many requests are allowed for an API, and over what window.
   *
   * @param maxAllowed the maximum number of requests per window
   * @param ttlSeconds the window's length, in seconds
   */
  private record RateLimitRule(long maxAllowed, long ttlSeconds) {
  }

  /**
   * Per-API rate limits, applied both globally (per API, shared across every
   * caller) and per username (each caller's own budget) -- both checked against
   * the same rule. {@code userPrompt}'s limit is deliberately low: it's the one
   * operation that calls the LLM, the most expensive call this app makes and
   * the one most worth guarding on a free-tier Groq plan.
   */
  private static final Map<String, RateLimitRule> RATE_LIMITS =
      Map.of(API_SIGN_UP, new RateLimitRule(30, 60), API_LOGIN,
          new RateLimitRule(30, 60), API_CREATE_CHAT, new RateLimitRule(30, 60),
          API_ALL_CHATS, new RateLimitRule(30, 60), API_GET_CHAT_BY_ID,
          new RateLimitRule(30, 60), API_UPDATE_TITLE,
          new RateLimitRule(30, 60), API_USER_PROMPT, new RateLimitRule(5, 60),
          API_SEARCH_CHAT, new RateLimitRule(30, 60));

  /**
   * Constructs a new {@code Core} wiring the Gate, Orchestrator, and Worker
   * services together.
   *
   * @param chatService handles user sign-up and login
   * @param orchestrator routes chat requests to the appropriate worker
   * @param worker executes chat requests against the configured LLM
   * @param authConfig configuration for encrypting/decrypting auth tokens
   * @param pingService checks database connectivity for the health endpoint
   * @param limitService enforces per-API and per-username rate limits
   * @param searcher searches the requesting user's chats
   */
  public Core(final UserChatService chatService,
      final OrchestratorService orchestrator, final WorkerService worker,
      final AuthProperties authConfig, final CronPingService pingService,
      final RateLimitService limitService, final SearchService searcher) {
    this.userChatService =
        Objects.requireNonNull(chatService, "userChatService is null");
    this.orchestratorService =
        Objects.requireNonNull(orchestrator, "orchestratorService is null");
    this.workerService =
        Objects.requireNonNull(worker, "workerService is null");
    this.authProperties =
        Objects.requireNonNull(authConfig, "authProperties is null");
    this.cronPingService =
        Objects.requireNonNull(pingService, "cronPingService is null");
    this.rateLimitService =
        Objects.requireNonNull(limitService, "rateLimitService is null");
    this.searchService =
        Objects.requireNonNull(searcher, "searchService is null");
  }

  /**
   * Enforces the configured rate limit for {@code api}, both globally and per
   * {@code username}.
   *
   * @param api the name of the operation being rate-limited
   * @param username the requesting user
   * @throws RateLimitExceededException if either limit has been exceeded
   */
  private void enforceRateLimit(final String api, final String username) {
    RateLimitRule rule = RATE_LIMITS.get(api);
    if (rule == null) {
      return;
    }
    boolean apiAllowed =
        rateLimitService.isAllowed(api, rule.maxAllowed(), rule.ttlSeconds());
    boolean userAllowed = rateLimitService.isAllowed(api + ":" + username,
        rule.maxAllowed(), rule.ttlSeconds());
    if (!apiAllowed || !userAllowed) {
      throw new RateLimitExceededException(api);
    }
  }

  /**
   * Registers a new user.
   *
   * @param request the requested username and password
   * @return a {@link UserGateResponsePOJO} carrying the encrypted auth token in
   *         the {@code X-Auth-Token} header on success, or an
   *         {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO signUp(final UserGateRequestPOJO request) {
    try {
      enforceRateLimit(API_SIGN_UP, request.getUsername());
      ValidatorUtils.validatePassword(request.getPassword());
      UserObject user =
          userChatService.signUp(request.getUsername(), request.getPassword());
      return buildGateResponse(HttpStatus.CREATED, user);
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Authenticates a user.
   *
   * @param request the username and password to authenticate
   * @return a {@link UserGateResponsePOJO} carrying the encrypted auth token in
   *         the {@code X-Auth-Token} header on success, or an
   *         {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO login(final UserGateRequestPOJO request) {
    try {
      enforceRateLimit(API_LOGIN, request.getUsername());
      UserObject user =
          userChatService.login(request.getUsername(), request.getPassword());
      return buildGateResponse(HttpStatus.OK, user);
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Creates a new chat for the requesting user.
   *
   * @param request the auth-token header and title for the new chat
   * @return a {@link ListOfChatResponsePOJO} with every chat owned by the user,
   *         newest first, including the one just created, or an
   *         {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO createChat(final ChatRequestPOJO request) {
    try {
      String username = extractUsername(request);
      enforceRateLimit(API_CREATE_CHAT, username);
      List<ChatObject> chatList =
          userChatService.createChat(username, request.getChatTitle());
      return ListOfChatResponsePOJO.builder().httpStatusCode(HttpStatus.CREATED)
          .timestamp(LocalDateTime.now()).chats(chatList).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Lists every chat owned by the requesting user.
   *
   * @param request the auth-token header identifying the user
   * @return a {@link ListOfChatResponsePOJO} with every chat owned by the user,
   *         newest first, or an {@link ErrorResponsePOJO} describing the
   *         failure
   */
  public ResponsePOJO allChats(final ChatRequestPOJO request) {
    try {
      String username = extractUsername(request);
      enforceRateLimit(API_ALL_CHATS, username);
      List<ChatObject> chatList = userChatService.listChats(username);
      return ListOfChatResponsePOJO.builder().httpStatusCode(HttpStatus.OK)
          .timestamp(LocalDateTime.now()).chats(chatList).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Fetches a single chat owned by the requesting user.
   *
   * @param request the auth-token header and chat identifier to fetch
   * @return a {@link ChatResponsePOJO} with the requested chat, or an
   *         {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO getChatById(final ChatRequestPOJO request) {
    try {
      String username = extractUsername(request);
      enforceRateLimit(API_GET_CHAT_BY_ID, username);
      ChatObject chat =
          userChatService.getChatById(username, request.getChatId());
      return buildChatResponse(HttpStatus.OK, chat);
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Renames a chat owned by the requesting user.
   *
   * @param request the auth-token header, chat identifier, and new title
   * @return a {@link ChatResponsePOJO} with the renamed chat, or an
   *         {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO updateTitle(final ChatRequestPOJO request) {
    try {
      String username = extractUsername(request);
      enforceRateLimit(API_UPDATE_TITLE, username);
      List<ChatObject> chatList = userChatService.updateChatTitle(username,
          request.getChatId(), request.getChatTitle());
      ChatObject updatedChat = chatList.stream()
          .filter(chat -> chat.getChatId().equals(request.getChatId()))
          .findFirst()
          .orElseThrow(() -> new ChatNotFoundException(request.getChatId()));
      return buildChatResponse(HttpStatus.OK, updatedChat);
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Answers the user's latest message in a chat, persisting both the user's
   * message and the generated reply.
   *
   * @param request the auth-token header, chat identifier, and message to send
   * @return a {@link ChatResponsePOJO} with the chat, including the new reply,
   *         or an {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO userPrompt(final ChatRequestPOJO request) {
    try {
      String username = extractUsername(request);
      enforceRateLimit(API_USER_PROMPT, username);
      ChatObject existingChat =
          userChatService.getChatById(username, request.getChatId());
      List<Message> history = existingChat.getMessages();
      OrchestratorResponse routing =
          orchestratorService.route(history, request.getMessage());
      log.info("Orchestrator routed to {} ({})", routing.getRequiredContexts(),
          routing.getReason());
      String answer = workerService.respond(routing.getRequiredContexts(),
          history, request.getMessage());
      userChatService.saveUserMessage(username, request.getChatId(),
          request.getMessage());
      ChatObject updatedChat = userChatService.saveAssistantMessage(username,
          request.getChatId(), answer);
      return buildChatResponse(HttpStatus.OK, updatedChat);
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Searches the requesting user's chats.
   *
   * @param request the auth-token header and search text
   * @return a {@link SearchResponsePOJO} with matching chats (highest score
   *         first) and their scores, or an {@link ErrorResponsePOJO} describing
   *         the failure
   */
  public ResponsePOJO searchChat(final SearchRequestPOJO request) {
    try {
      String username = extractUsername(request);
      enforceRateLimit(API_SEARCH_CHAT, username);
      List<ChatSearchResult> results =
          searchService.processUserQuery(username, request.getQuery());
      Map<String, ChatObject> chatsById =
          userChatService.listChats(username).stream().collect(
              Collectors.toMap(ChatObject::getChatId, Function.identity()));
      List<ChatObject> chats =
          results.stream().map(result -> chatsById.get(result.getChatId()))
              .filter(Objects::nonNull).toList();
      Map<String, Double> scores = results.stream()
          .collect(Collectors.toMap(ChatSearchResult::getChatId,
              ChatSearchResult::getScore, (first, second) -> first,
              LinkedHashMap::new));
      return SearchResponsePOJO.builder().httpStatusCode(HttpStatus.OK)
          .timestamp(LocalDateTime.now()).chats(chats).scores(scores).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Checks database connectivity.
   *
   * @return a plain {@link ResponsePOJO} with status 200 if the database is
   *         reachable, or an {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO health() {
    try {
      cronPingService.pingDatabase();
      return ResponsePOJO.builder().httpStatusCode(HttpStatus.OK)
          .timestamp(LocalDateTime.now()).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Recovers the requesting username from the {@code X-Auth-Token} header every
   * non-Gate request must carry.
   *
   * @param request the request to read the header from
   * @return the username the token was issued for
   * @throws TokenException if the header is missing or the token cannot be
   *         decrypted
   */
  private String extractUsername(final RequestPOJO request) {
    String token = request.getHeaders().get(X_AUTH_TOKEN);
    if (StringUtils.isEmpty(token)) {
      throw new TokenException("Missing " + X_AUTH_TOKEN + " header.", null);
    }
    return TokenUtils.decrypt(token, authProperties.secretKey());
  }

  private UserGateResponsePOJO buildGateResponse(final HttpStatus status,
      final UserObject user) {
    String authToken =
        TokenUtils.encrypt(user.getUsername(), authProperties.secretKey());
    return UserGateResponsePOJO.builder().httpStatusCode(status)
        .timestamp(LocalDateTime.now()).headers(Map.of(X_AUTH_TOKEN, authToken))
        .build();
  }

  private ChatResponsePOJO buildChatResponse(final HttpStatus status,
      final ChatObject chat) {
    return ChatResponsePOJO.builder().httpStatusCode(status)
        .timestamp(LocalDateTime.now()).chat(chat).build();
  }

  private ErrorResponsePOJO buildErrorResponse(final Exception exception) {
    return switch (exception) {
      // Client-caused: safe to show the exception's own message.
      case InvalidPasswordException _ ->
        knownError(HttpStatus.BAD_REQUEST, exception);
      case InvalidEmailException _ ->
        knownError(HttpStatus.BAD_REQUEST, exception);
      case IllegalArgumentException _ ->
        knownError(HttpStatus.BAD_REQUEST, exception);
      case DuplicateUsernameException _ ->
        knownError(HttpStatus.CONFLICT, exception);
      case UserNotFoundException _ ->
        knownError(HttpStatus.NOT_FOUND, exception);
      case ChatNotFoundException _ ->
        knownError(HttpStatus.NOT_FOUND, exception);
      case ChatAccessDeniedException _ ->
        knownError(HttpStatus.FORBIDDEN, exception);
      case InvalidCredentialsException _ ->
        knownError(HttpStatus.UNAUTHORIZED, exception);
      case RateLimitExceededException _ ->
        knownError(HttpStatus.TOO_MANY_REQUESTS, exception);
      // Every non-Gate request must carry a valid X-Auth-Token; almost
      // always this means the header is missing/tampered/expired, not an
      // internal crypto failure, so it is safe to show and unauthorized.
      case TokenException _ -> knownError(HttpStatus.UNAUTHORIZED, exception);

      // Internal failures: known cause, message not safe to expose.
      case DatabaseOperationException _ -> genericError();
      case ValkeyCacheException _ -> genericError();
      case KafkaOperationException _ -> genericError();
      case KafkaConsumerAlreadyStartedException _ -> genericError();
      case OpenSearchOperationException _ -> genericError();
      case CacheSetException _ -> genericError();
      case JsonExtractionException _ -> genericError();

      // Third-party API calls: the failure is upstream, not this service.
      case GroqCallException _ -> upstreamError();
      case GitHubCallException _ -> upstreamError();
      case CodeforcesCallException _ -> upstreamError();
      case LeetcodeCallException _ -> upstreamError();
      case ProfileCallException _ -> upstreamError();

      // Safety net for a future RepositoryException subtype not yet
      // given its own case above.
      case RepositoryException _ -> genericError();

      default -> genericError();
    };
  }

  private ErrorResponsePOJO knownError(final HttpStatus status,
      final Exception exception) {
    return ErrorResponsePOJO.builder().httpStatusCode(status)
        .timestamp(LocalDateTime.now()).showMessageAsIs(true)
        .errorMessage(exception.getMessage()).build();
  }

  private ErrorResponsePOJO genericError() {
    return ErrorResponsePOJO.builder()
        .httpStatusCode(HttpStatus.INTERNAL_SERVER_ERROR)
        .timestamp(LocalDateTime.now()).showMessageAsIs(false)
        .errorMessage(INTERNAL_ERROR_MESSAGE).build();
  }

  private ErrorResponsePOJO upstreamError() {
    return ErrorResponsePOJO.builder().httpStatusCode(HttpStatus.BAD_GATEWAY)
        .timestamp(LocalDateTime.now()).showMessageAsIs(false)
        .errorMessage(UPSTREAM_ERROR_MESSAGE).build();
  }
}
