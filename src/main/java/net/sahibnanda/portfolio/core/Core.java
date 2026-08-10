package net.sahibnanda.portfolio.core;

import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.AuthProperties;
import net.sahibnanda.portfolio.config.ChatLimitsProperties;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.exception.CacheSetException;
import net.sahibnanda.portfolio.exception.ChatAccessDeniedException;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.exception.CodeforcesCallException;
import net.sahibnanda.portfolio.exception.ConversationTooLongException;
import net.sahibnanda.portfolio.exception.DatabaseOperationException;
import net.sahibnanda.portfolio.exception.DuplicateUsernameException;
import net.sahibnanda.portfolio.exception.GitHubCallException;
import net.sahibnanda.portfolio.exception.GroqCallException;
import net.sahibnanda.portfolio.exception.HealthCheckException;
import net.sahibnanda.portfolio.exception.InputTooLongException;
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
import net.sahibnanda.portfolio.exception.SessionResolutionException;
import net.sahibnanda.portfolio.exception.TokenException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.exception.ValkeyCacheException;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.objects.ChatSearchResult;
import net.sahibnanda.portfolio.objects.CodeforcesDetails;
import net.sahibnanda.portfolio.objects.GitHubDetails;
import net.sahibnanda.portfolio.objects.LeetcodeDetails;
import net.sahibnanda.portfolio.objects.OrchestratorResponse;
import net.sahibnanda.portfolio.objects.PersonalityDetails;
import net.sahibnanda.portfolio.objects.ProfessionalDetails;
import net.sahibnanda.portfolio.objects.ProfileDetails;
import net.sahibnanda.portfolio.objects.UserObject;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.ChatStreamDonePOJO;
import net.sahibnanda.portfolio.pojo.ChatStreamTokenPOJO;
import net.sahibnanda.portfolio.pojo.CodeforcesDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ErrorResponsePOJO;
import net.sahibnanda.portfolio.pojo.GitHubDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.LeetcodeDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ListOfChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.PersonalityDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ProfessionalDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ProfileDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.RequestPOJO;
import net.sahibnanda.portfolio.pojo.ResponsePOJO;
import net.sahibnanda.portfolio.pojo.SearchRequestPOJO;
import net.sahibnanda.portfolio.pojo.SearchResponsePOJO;
import net.sahibnanda.portfolio.pojo.UserGateRequestPOJO;
import net.sahibnanda.portfolio.pojo.UserGateResponsePOJO;
import net.sahibnanda.portfolio.services.AnonymousChatService;
import net.sahibnanda.portfolio.services.CronPingService;
import net.sahibnanda.portfolio.services.DetailsService;
import net.sahibnanda.portfolio.services.OrchestratorService;
import net.sahibnanda.portfolio.services.RateLimitService;
import net.sahibnanda.portfolio.services.SearchService;
import net.sahibnanda.portfolio.services.UserChatService;
import net.sahibnanda.portfolio.services.WorkerService;
import net.sahibnanda.portfolio.utils.ConversationHistoryUtils;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import net.sahibnanda.portfolio.utils.TokenUtils;
import net.sahibnanda.portfolio.utils.ValidatorUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

  /** API name used to key {@link #professionalDetails}'s rate limit. */
  private static final String API_PROFESSIONAL_DETAILS = "professionalDetails";

  /** API name used to key {@link #leetcodeDetails}'s rate limit. */
  private static final String API_LEETCODE_DETAILS = "leetcodeDetails";

  /** API name used to key {@link #codeforcesDetails}'s rate limit. */
  private static final String API_CODEFORCES_DETAILS = "codeforcesDetails";

  /** API name used to key {@link #githubDetails}'s rate limit. */
  private static final String API_GITHUB_DETAILS = "githubDetails";

  /** API name used to key {@link #personalityDetails}'s rate limit. */
  private static final String API_PERSONALITY_DETAILS = "personalityDetails";

  /** API name used to key {@link #profileDetails}'s rate limit. */
  private static final String API_PROFILE_DETAILS = "profileDetails";

  /** Handles user sign-up and login against the users table. */
  private final UserChatService userChatService;

  /** Handles ephemeral, session-scoped chats for anonymous visitors. */
  private final AnonymousChatService anonymousChatService;

  /** Routes chat requests to the appropriate worker. */
  private final OrchestratorService orchestratorService;

  /** Executes chat requests against the configured LLM. */
  private final WorkerService workerService;

  /** Configuration for encrypting/decrypting auth tokens. */
  private final AuthProperties authProperties;

  /** Checks every infrastructure dependency for the health endpoint. */
  private final CronPingService cronPingService;

  /** Enforces per-API and per-username rate limits. */
  private final RateLimitService rateLimitService;

  /** Searches the requesting user's chats. */
  private final SearchService searchService;

  /** Fetches the portfolio owner's professional links. */
  private final DetailsService detailsService;

  /** Caps on caller-supplied chat input sizes and per-turn LLM context. */
  private final ChatLimitsProperties chatLimits;

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
      Map.ofEntries(Map.entry(API_SIGN_UP, new RateLimitRule(30, 60)),
          Map.entry(API_LOGIN, new RateLimitRule(30, 60)),
          Map.entry(API_CREATE_CHAT, new RateLimitRule(30, 60)),
          Map.entry(API_ALL_CHATS, new RateLimitRule(30, 60)),
          Map.entry(API_GET_CHAT_BY_ID, new RateLimitRule(30, 60)),
          Map.entry(API_UPDATE_TITLE, new RateLimitRule(30, 60)),
          Map.entry(API_USER_PROMPT, new RateLimitRule(5, 60)),
          Map.entry(API_SEARCH_CHAT, new RateLimitRule(30, 60)),
          Map.entry(API_PROFESSIONAL_DETAILS, new RateLimitRule(30, 60)),
          Map.entry(API_LEETCODE_DETAILS, new RateLimitRule(30, 60)),
          Map.entry(API_CODEFORCES_DETAILS, new RateLimitRule(30, 60)),
          Map.entry(API_GITHUB_DETAILS, new RateLimitRule(30, 60)),
          Map.entry(API_PERSONALITY_DETAILS, new RateLimitRule(30, 60)),
          Map.entry(API_PROFILE_DETAILS, new RateLimitRule(30, 60)));

  /**
   * Constructs a new {@code Core} wiring the Gate, Orchestrator, and Worker
   * services together.
   *
   * @param chatService handles user sign-up and login
   * @param anonymousChats handles ephemeral, session-scoped chats for anonymous
   *        visitors
   * @param orchestrator routes chat requests to the appropriate worker
   * @param worker executes chat requests against the configured LLM
   * @param authConfig configuration for encrypting/decrypting auth tokens
   * @param pingService checks every infrastructure dependency for the health
   *        endpoint
   * @param limitService enforces per-API and per-username rate limits
   * @param searcher searches the requesting user's chats
   * @param detailsFetcher fetches the portfolio owner's professional links
   * @param chatLimitsConfig caps on caller-supplied chat input sizes and
   *        per-turn LLM context
   */
  public Core(final UserChatService chatService,
      final AnonymousChatService anonymousChats,
      final OrchestratorService orchestrator, final WorkerService worker,
      final AuthProperties authConfig, final CronPingService pingService,
      final RateLimitService limitService, final SearchService searcher,
      final DetailsService detailsFetcher,
      final ChatLimitsProperties chatLimitsConfig) {
    this.userChatService =
        Objects.requireNonNull(chatService, "userChatService is null");
    this.anonymousChatService =
        Objects.requireNonNull(anonymousChats, "anonymousChatService is null");
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
    this.detailsService =
        Objects.requireNonNull(detailsFetcher, "detailsService is null");
    this.chatLimits =
        Objects.requireNonNull(chatLimitsConfig, "chatLimits is null");
  }

  /**
   * Enforces the configured rate limit for {@code api}, globally, per
   * {@code username}, and per {@code clientIp} -- the IP tier exists
   * specifically so rotating {@code X-Session-Id}/usernames can't be used to
   * dodge the per-caller budget, since IP is the hardest-to-forge signal
   * available here.
   *
   * @param api the name of the operation being rate-limited
   * @param username the requesting user
   * @param clientIp the requesting caller's IP address; the IP tier is skipped
   *        if blank (e.g. a request built without going through
   *        {@code Controller})
   * @throws RateLimitExceededException if any tier's limit has been exceeded
   */
  private void enforceRateLimit(final String api, final String username,
      final String clientIp) {
    RateLimitRule rule = RATE_LIMITS.get(api);
    if (rule == null) {
      return;
    }
    boolean apiAllowed =
        rateLimitService.isAllowed(api, rule.maxAllowed(), rule.ttlSeconds());
    boolean userAllowed = rateLimitService.isAllowed(api + ":" + username,
        rule.maxAllowed(), rule.ttlSeconds());
    boolean ipAllowed =
        StringUtils.isEmpty(clientIp) || rateLimitService.isAllowed(
            api + ":ip:" + clientIp, rule.maxAllowed(), rule.ttlSeconds());
    if (!apiAllowed || !userAllowed || !ipAllowed) {
      throw new RateLimitExceededException(api, rule.ttlSeconds());
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
      enforceRateLimit(API_SIGN_UP, request.getUsername(),
          request.getIpAddress());
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
      enforceRateLimit(API_LOGIN, request.getUsername(),
          request.getIpAddress());
      UserObject user =
          userChatService.login(request.getUsername(), request.getPassword());
      return buildGateResponse(HttpStatus.OK, user);
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Creates a new chat for the requesting caller. Authenticated callers
   * (carrying a valid {@code X-Auth-Token}) get a durable, username-owned chat;
   * anonymous callers get an ephemeral, session-id-owned chat instead.
   *
   * @param request the auth-token header and/or resolved session id, and the
   *        title for the new chat
   * @return for an authenticated caller, a {@link ListOfChatResponsePOJO} with
   *         every chat owned by the user, newest first, including the one just
   *         created; for an anonymous caller, the same envelope wrapping just
   *         the newly created chat; or an {@link ErrorResponsePOJO} describing
   *         the failure
   */
  public ResponsePOJO createChat(final ChatRequestPOJO request) {
    try {
      if (isAuthenticated(request)) {
        String username = extractUsername(request);
        enforceRateLimit(API_CREATE_CHAT, username, request.getIpAddress());
        ValidatorUtils.validateMaxLength(request.getChatTitle(), "chatTitle",
            chatLimits.maxChatTitleLength());
        List<ChatObject> chatList =
            userChatService.createChat(username, request.getChatTitle());
        return ListOfChatResponsePOJO.builder()
            .httpStatusCode(HttpStatus.CREATED).timestamp(LocalDateTime.now())
            .chats(chatList).build();
      }
      String sessionId = extractSessionId(request);
      enforceRateLimit(API_CREATE_CHAT, "anon:" + sessionId,
          request.getIpAddress());
      ValidatorUtils.validateMaxLength(request.getChatTitle(), "chatTitle",
          chatLimits.maxChatTitleLength());
      ChatObject chat =
          anonymousChatService.createChat(sessionId, request.getChatTitle());
      return ListOfChatResponsePOJO.builder().httpStatusCode(HttpStatus.CREATED)
          .timestamp(LocalDateTime.now()).chats(List.of(chat)).build();
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
      enforceRateLimit(API_ALL_CHATS, username, request.getIpAddress());
      List<ChatObject> chatList = userChatService.listChats(username);
      return ListOfChatResponsePOJO.builder().httpStatusCode(HttpStatus.OK)
          .timestamp(LocalDateTime.now()).chats(chatList).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Fetches a single chat owned by the requesting caller. Authenticated callers
   * are scoped to their username; anonymous callers are scoped to their
   * resolved session id.
   *
   * @param request the auth-token header and/or resolved session id, and the
   *        chat identifier to fetch
   * @return a {@link ChatResponsePOJO} with the requested chat, or an
   *         {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO getChatById(final ChatRequestPOJO request) {
    try {
      if (isAuthenticated(request)) {
        String username = extractUsername(request);
        enforceRateLimit(API_GET_CHAT_BY_ID, username, request.getIpAddress());
        ChatObject chat =
            userChatService.getChatById(username, request.getChatId());
        return buildChatResponse(HttpStatus.OK, chat);
      }
      String sessionId = extractSessionId(request);
      enforceRateLimit(API_GET_CHAT_BY_ID, "anon:" + sessionId,
          request.getIpAddress());
      ChatObject chat =
          anonymousChatService.getChatById(sessionId, request.getChatId());
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
      enforceRateLimit(API_UPDATE_TITLE, username, request.getIpAddress());
      ValidatorUtils.validateMaxLength(request.getChatTitle(), "chatTitle",
          chatLimits.maxChatTitleLength());
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
   * Answers the caller's latest message in a chat, persisting both the user's
   * message and the generated reply. Authenticated callers are scoped to their
   * username; anonymous callers are scoped to their resolved session id -- the
   * Orchestrator/Worker pipeline itself is identical either way.
   *
   * @param request the auth-token header and/or resolved session id, the chat
   *        identifier, and the message to send
   * @return a {@link ChatResponsePOJO} with the chat, including the new reply,
   *         or an {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO userPrompt(final ChatRequestPOJO request) {
    try {
      boolean authenticated = isAuthenticated(request);
      String callerId =
          authenticated ? extractUsername(request) : extractSessionId(request);
      String rateLimitKey = authenticated ? callerId : "anon:" + callerId;
      enforceRateLimit(API_USER_PROMPT, rateLimitKey, request.getIpAddress());
      ValidatorUtils.validateMaxLength(request.getMessage(), "message",
          chatLimits.maxMessageLength());

      ChatObject existingChat = authenticated
          ? userChatService.getChatById(callerId, request.getChatId())
          : anonymousChatService.getChatById(callerId, request.getChatId());
      List<Message> history = existingChat.getMessages();
      if (history.size() >= chatLimits.maxMessagesPerChat()) {
        throw new ConversationTooLongException(request.getChatId(),
            chatLimits.maxMessagesPerChat());
      }
      List<Message> boundedHistory = ConversationHistoryUtils.truncate(history,
          chatLimits.maxHistoryMessages(), chatLimits.maxHistoryChars());
      OrchestratorResponse routing =
          orchestratorService.route(boundedHistory, request.getMessage());
      log.info("Orchestrator routed to {} ({})", routing.getRequiredContexts(),
          routing.getReason());
      String answer = workerService.respond(routing.getRequiredContexts(),
          boundedHistory, request.getMessage());

      ChatObject updatedChat;
      if (authenticated) {
        userChatService.saveUserMessage(callerId, request.getChatId(),
            request.getMessage());
        updatedChat = userChatService.saveAssistantMessage(callerId,
            request.getChatId(), answer);
      } else {
        anonymousChatService.saveUserMessage(callerId, request.getChatId(),
            request.getMessage());
        updatedChat = anonymousChatService.saveAssistantMessage(callerId,
            request.getChatId(), answer);
      }
      return buildChatResponse(HttpStatus.OK, updatedChat);
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Runs every validation, rate-limit, and routing step {@link #userPrompt}
   * runs today, stopping just short of calling the Worker AI -- the streaming
   * counterpart to {@code userPrompt}'s setup phase, used by the SSE endpoint
   * so the Worker call itself can stream tokens back to the caller instead of
   * blocking for the full answer.
   *
   * @param request the auth-token header and/or resolved session id, the chat
   *        identifier, and the message to send
   * @return a non-error {@link ChatStreamHandoff} whose {@code context} means
   *         routing succeeded and {@link #streamWorkerAnswer} can proceed; an
   *         error {@link ChatStreamHandoff} means the caller should send the
   *         embedded {@link ErrorResponsePOJO} back as a normal JSON error
   *         response, without ever opening an SSE stream
   */
  public ChatStreamHandoff prepareUserPromptStream(
      final ChatRequestPOJO request) {
    try {
      boolean authenticated = isAuthenticated(request);
      String callerId =
          authenticated ? extractUsername(request) : extractSessionId(request);
      String rateLimitKey = authenticated ? callerId : "anon:" + callerId;
      enforceRateLimit(API_USER_PROMPT, rateLimitKey, request.getIpAddress());
      ValidatorUtils.validateMaxLength(request.getMessage(), "message",
          chatLimits.maxMessageLength());

      ChatObject existingChat = authenticated
          ? userChatService.getChatById(callerId, request.getChatId())
          : anonymousChatService.getChatById(callerId, request.getChatId());
      List<Message> history = existingChat.getMessages();
      if (history.size() >= chatLimits.maxMessagesPerChat()) {
        throw new ConversationTooLongException(request.getChatId(),
            chatLimits.maxMessagesPerChat());
      }
      List<Message> boundedHistory = ConversationHistoryUtils.truncate(history,
          chatLimits.maxHistoryMessages(), chatLimits.maxHistoryChars());
      OrchestratorResponse routing =
          orchestratorService.route(boundedHistory, request.getMessage());
      log.info("Orchestrator routed to {} ({})", routing.getRequiredContexts(),
          routing.getReason());

      return new ChatStreamHandoff(new ChatStreamContext(authenticated,
          callerId, request.getChatId(), request.getMessage(), boundedHistory,
          routing.getRequiredContexts()), null);
    } catch (Exception e) {
      return new ChatStreamHandoff(null, buildErrorResponse(e));
    }
  }

  /**
   * Streams the Worker AI's answer to {@code emitter} as it is generated, then
   * persists both the user's message and the full assistant answer once the
   * stream completes successfully. Sends zero-or-more SSE {@code token} events
   * (each carrying a {@link ChatStreamTokenPOJO}), followed by exactly one
   * terminal event: {@code done} (carrying a {@link ChatStreamDonePOJO}) on
   * success, or {@code error} (carrying an {@link ErrorResponsePOJO}) on
   * failure. {@code emitter.complete()} is always called after the terminal
   * event, regardless of outcome.
   *
   * @param context the prepared streaming context returned by a non-error
   *        {@link #prepareUserPromptStream}
   * @param emitter the SSE emitter to stream events to
   */
  public void streamWorkerAnswer(final ChatStreamContext context,
      final SseEmitter emitter) {
    StringBuilder accumulated = new StringBuilder();
    try {
      String answer = workerService.respondStream(context.requiredContexts(),
          context.boundedHistory(), context.userMessage(), delta -> {
            accumulated.append(delta);
            sendEvent(emitter, "token", context.chatId(),
                ChatStreamTokenPOJO.builder().content(delta).build());
          });

      ChatObject updatedChat;
      if (context.authenticated()) {
        userChatService.saveUserMessage(context.callerId(), context.chatId(),
            context.userMessage());
        updatedChat = userChatService.saveAssistantMessage(context.callerId(),
            context.chatId(), answer);
      } else {
        anonymousChatService.saveUserMessage(context.callerId(),
            context.chatId(), context.userMessage());
        updatedChat = anonymousChatService
            .saveAssistantMessage(context.callerId(), context.chatId(), answer);
      }
      Message lastMessage = updatedChat.getMessages().getLast();
      sendEvent(emitter, "done", context.chatId(), ChatStreamDonePOJO.builder()
          .message(answer).timestamp(lastMessage.timestamp()).build());
      emitter.complete();
    } catch (GroqCallException e) {
      log.error("Groq streaming call failed for chat {}", context.chatId(), e);
      sendEvent(emitter, "error", context.chatId(), upstreamError());
      emitter.complete();
    } catch (Exception e) {
      log.error("Unexpected failure while streaming chat {}", context.chatId(),
          e);
      sendEvent(emitter, "error", context.chatId(), genericError());
      emitter.complete();
    }
  }

  /**
   * Sends a single named SSE event with a JSON-serialized payload, swallowing
   * (and logging) an {@link IOException} if the client has already disconnected
   * -- there is nothing further this method can do about a broken connection,
   * and the stream is already being torn down by its caller regardless.
   *
   * @param emitter the SSE emitter to send the event on
   * @param name the SSE event name (e.g. {@code "token"}, {@code "done"},
   *        {@code "error"})
   * @param chatId the chat identifier, used only for logging on failure
   * @param data the event payload, serialized to JSON
   */
  private void sendEvent(final SseEmitter emitter, final String name,
      final String chatId, final Object data) {
    try {
      emitter.send(SseEmitter.event().name(name).data(JsonUtils.toJson(data),
          MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      log.warn("Client disconnected mid-stream for chat {}", chatId, e);
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
      enforceRateLimit(API_SEARCH_CHAT, username, request.getIpAddress());
      ValidatorUtils.validateMaxLength(request.getQuery(), "query",
          chatLimits.maxSearchQueryLength());
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
   * Checks connectivity to every infrastructure dependency (Postgres, Valkey,
   * Kafka, OpenSearch).
   *
   * @return a plain {@link ResponsePOJO} with status 200 if every dependency is
   *         reachable, or a 503 {@link ErrorResponsePOJO} naming the
   *         dependencies that failed to respond
   */
  public ResponsePOJO health() {
    try {
      cronPingService.ping();
      return ResponsePOJO.builder().httpStatusCode(HttpStatus.OK)
          .timestamp(LocalDateTime.now()).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Returns the portfolio owner's professional links: LeetCode, Codeforces, and
   * GitHub profile links, the resume link, and profile photo link(s).
   *
   * @return a {@link ProfessionalDetailsResponsePOJO} with the professional
   *         details, or an {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO professionalDetails() {
    try {
      enforceGlobalRateLimit(API_PROFESSIONAL_DETAILS);
      ProfessionalDetails details = detailsService.getProfessionalDetails();
      return ProfessionalDetailsResponsePOJO.builder()
          .httpStatusCode(HttpStatus.OK).timestamp(LocalDateTime.now())
          .professionalDetails(details).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Returns details for every configured LeetCode account.
   *
   * @return a {@link LeetcodeDetailsResponsePOJO} with the LeetCode details, or
   *         an {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO leetcodeDetails() {
    try {
      enforceGlobalRateLimit(API_LEETCODE_DETAILS);
      List<LeetcodeDetails> details = Objects
          .requireNonNullElse(detailsService.getLeetcodeDetails(), List.of());
      return LeetcodeDetailsResponsePOJO.builder().httpStatusCode(HttpStatus.OK)
          .timestamp(LocalDateTime.now()).leetcodeDetails(details).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Returns details for every configured Codeforces account.
   *
   * @return a {@link CodeforcesDetailsResponsePOJO} with the Codeforces
   *         details, or an {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO codeforcesDetails() {
    try {
      enforceGlobalRateLimit(API_CODEFORCES_DETAILS);
      List<CodeforcesDetails> details = Objects
          .requireNonNullElse(detailsService.getCodeforcesDetails(), List.of());
      return CodeforcesDetailsResponsePOJO.builder()
          .httpStatusCode(HttpStatus.OK).timestamp(LocalDateTime.now())
          .codeforcesDetails(details).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Returns details for every configured GitHub account.
   *
   * @return a {@link GitHubDetailsResponsePOJO} with the GitHub details, or an
   *         {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO githubDetails() {
    try {
      enforceGlobalRateLimit(API_GITHUB_DETAILS);
      List<GitHubDetails> details = Objects
          .requireNonNullElse(detailsService.getGithubDetails(), List.of());
      return GitHubDetailsResponsePOJO.builder().httpStatusCode(HttpStatus.OK)
          .timestamp(LocalDateTime.now()).githubDetails(details).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Returns the portfolio owner's personality profile.
   *
   * @return a {@link PersonalityDetailsResponsePOJO} with the personality
   *         details, or an {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO personalityDetails() {
    try {
      enforceGlobalRateLimit(API_PERSONALITY_DETAILS);
      PersonalityDetails details =
          Objects.requireNonNull(detailsService.getPersonalityDetails(),
              "getPersonalityDetails() returned null");
      return PersonalityDetailsResponsePOJO.builder()
          .httpStatusCode(HttpStatus.OK).timestamp(LocalDateTime.now())
          .personalityDetails(details).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Returns the portfolio owner's profile.
   *
   * @return a {@link ProfileDetailsResponsePOJO} with the profile details, or
   *         an {@link ErrorResponsePOJO} describing the failure
   */
  public ResponsePOJO profileDetails() {
    try {
      enforceGlobalRateLimit(API_PROFILE_DETAILS);
      ProfileDetails details =
          Objects.requireNonNull(detailsService.getProfileDetails(),
              "getProfileDetails() returned null");
      return ProfileDetailsResponsePOJO.builder().httpStatusCode(HttpStatus.OK)
          .timestamp(LocalDateTime.now()).profileDetails(details).build();
    } catch (Exception e) {
      return buildErrorResponse(e);
    }
  }

  /**
   * Enforces the configured rate limit for {@code api}, applied only globally
   * (shared across every caller) -- for endpoints with no authenticated caller
   * to key a per-username limit on.
   *
   * @param api the name of the operation being rate-limited
   * @throws RateLimitExceededException if the limit has been exceeded
   */
  private void enforceGlobalRateLimit(final String api) {
    RateLimitRule rule = RATE_LIMITS.get(api);
    if (rule == null) {
      return;
    }
    if (!rateLimitService.isAllowed(api, rule.maxAllowed(),
        rule.ttlSeconds())) {
      throw new RateLimitExceededException(api, rule.ttlSeconds());
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

  /**
   * Whether the request carries a usable {@code X-Auth-Token} header, i.e.
   * whether it should be handled by the authenticated (username-scoped) path
   * rather than the anonymous (session-scoped) one.
   *
   * @param request the request to check
   * @return true if the request carries a non-blank {@code X-Auth-Token}
   */
  private boolean isAuthenticated(final RequestPOJO request) {
    return !StringUtils.isEmpty(request.getHeaders().get(X_AUTH_TOKEN));
  }

  /**
   * Recovers the anonymous session id resolved by the middleware package's
   * {@code SessionHeaderResolver} and threaded onto the request by
   * {@code Controller}.
   *
   * @param request the request to read the session id from
   * @return the resolved session id
   * @throws SessionResolutionException if no session id was resolved -- should
   *         never happen when the request went through {@code Controller}, so
   *         this signals a server-side invariant violation
   */
  private String extractSessionId(final RequestPOJO request) {
    String sessionId = request.getSessionId();
    if (StringUtils.isEmpty(sessionId)) {
      throw new SessionResolutionException(
          "No session id was resolved for this request.");
    }
    return sessionId;
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
      case InputTooLongException _ ->
        knownError(HttpStatus.BAD_REQUEST, exception);
      case ConversationTooLongException _ ->
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
      case RateLimitExceededException rle -> ErrorResponsePOJO.builder()
          .httpStatusCode(HttpStatus.TOO_MANY_REQUESTS)
          .timestamp(LocalDateTime.now()).showMessageAsIs(true)
          .errorMessage(rle.getMessage())
          .headers(
              Map.of("Retry-After", String.valueOf(rle.getRetryAfterSeconds())))
          .build();
      // Every non-Gate request must carry a valid X-Auth-Token; almost
      // always this means the header is missing/tampered/expired, not an
      // internal crypto failure, so it is safe to show and unauthorized.
      case TokenException _ -> knownError(HttpStatus.UNAUTHORIZED, exception);

      // Internal failures: known cause, message not safe to expose.
      // Signals Controller failed to resolve a session id before calling
      // Core -- a server-side invariant violation, not a client mistake.
      case SessionResolutionException _ -> genericError();
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

      // /health's whole purpose is telling the caller which dependency is
      // down; the message only ever names infra types (e.g. "postgres,
      // kafka"), so it's safe to expose as-is.
      case HealthCheckException _ ->
        knownError(HttpStatus.SERVICE_UNAVAILABLE, exception);

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
