package net.sahibnanda.portfolio.controller;

import net.sahibnanda.portfolio.core.Core;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ResponsePOJO;
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
   * Constructs a new controller.
   *
   * @param coreService wires every operation this controller exposes
   */
  public Controller(final Core coreService) {
    this.core = Objects.requireNonNull(coreService, "core is null");
  }

  /**
   * Registers a new user.
   *
   * @param request the requested username and password
   * @return the created user's auth token, or a failure response
   */
  @PostMapping("/signup")
  public ResponseEntity<ResponsePOJO> signUp(
      @RequestBody final UserGateRequestPOJO request) {
    return toResponseEntity(core.signUp(request));
  }

  /**
   * Authenticates a user.
   *
   * @param request the username and password to authenticate
   * @return the authenticated user's auth token, or a failure response
   */
  @PostMapping("/login")
  public ResponseEntity<ResponsePOJO> login(
      @RequestBody final UserGateRequestPOJO request) {
    return toResponseEntity(core.login(request));
  }

  /**
   * Creates a new chat for the requesting user.
   *
   * @param authToken the caller's {@code X-Auth-Token}
   * @param request the new chat's title
   * @return every chat owned by the user, or a failure response
   */
  @PostMapping("/chats")
  public ResponseEntity<ResponsePOJO> createChat(
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      @RequestBody final ChatRequestPOJO request) {
    return toResponseEntity(core.createChat(withAuthToken(request, authToken)));
  }

  /**
   * Lists every chat owned by the requesting user.
   *
   * @param authToken the caller's {@code X-Auth-Token}
   * @return every chat owned by the user, or a failure response
   */
  @GetMapping("/chats")
  public ResponseEntity<ResponsePOJO> allChats(@RequestHeader(
      value = X_AUTH_TOKEN, required = false) final String authToken) {
    ChatRequestPOJO request =
        withAuthToken(ChatRequestPOJO.builder().build(), authToken);
    return toResponseEntity(core.allChats(request));
  }

  /**
   * Fetches a single chat owned by the requesting user.
   *
   * @param chatId the identifier of the chat to fetch
   * @param authToken the caller's {@code X-Auth-Token}
   * @return the requested chat, or a failure response
   */
  @GetMapping("/chats/{chatId}")
  public ResponseEntity<ResponsePOJO> getChatById(
      @PathVariable final String chatId, @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken) {
    ChatRequestPOJO request = withAuthToken(
        ChatRequestPOJO.builder().chatId(chatId).build(), authToken);
    return toResponseEntity(core.getChatById(request));
  }

  /**
   * Renames a chat owned by the requesting user.
   *
   * @param chatId the identifier of the chat to rename
   * @param authToken the caller's {@code X-Auth-Token}
   * @param request the new title
   * @return the renamed chat, or a failure response
   */
  @PatchMapping("/chats/{chatId}")
  public ResponseEntity<ResponsePOJO> updateTitle(
      @PathVariable final String chatId,
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      @RequestBody final ChatRequestPOJO request) {
    ChatRequestPOJO merged =
        withAuthToken(request.toBuilder().chatId(chatId).build(), authToken);
    return toResponseEntity(core.updateTitle(merged));
  }

  /**
   * Answers the user's latest message in a chat.
   *
   * @param chatId the identifier of the chat to send the message to
   * @param authToken the caller's {@code X-Auth-Token}
   * @param request the message to send
   * @return the chat, including the new reply, or a failure response
   */
  @PostMapping("/chats/{chatId}/messages")
  public ResponseEntity<ResponsePOJO> userPrompt(
      @PathVariable final String chatId,
      @RequestHeader(value = X_AUTH_TOKEN,
          required = false) final String authToken,
      @RequestBody final ChatRequestPOJO request) {
    ChatRequestPOJO merged =
        withAuthToken(request.toBuilder().chatId(chatId).build(), authToken);
    return toResponseEntity(core.userPrompt(merged));
  }

  /**
   * Checks database connectivity.
   *
   * @return 200 if the database is reachable, or a failure response
   */
  @GetMapping("/health")
  public ResponseEntity<ResponsePOJO> health() {
    return toResponseEntity(core.health());
  }

  private static ChatRequestPOJO withAuthToken(final ChatRequestPOJO request,
      final String authToken) {
    Map<String, String> headers =
        authToken == null ? Map.of() : Map.of(X_AUTH_TOKEN, authToken);
    return request.toBuilder().headers(headers).build();
  }

  private static ResponseEntity<ResponsePOJO> toResponseEntity(
      final ResponsePOJO response) {
    HttpHeaders httpHeaders = new HttpHeaders();
    response.getHeaders().forEach(httpHeaders::add);
    return ResponseEntity.status(response.getHttpStatusCode())
        .headers(httpHeaders).body(response);
  }
}
