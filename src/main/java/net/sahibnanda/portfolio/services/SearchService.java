package net.sahibnanda.portfolio.services;

import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.api.OpenSearchAPI;
import net.sahibnanda.portfolio.config.ChatObserverProperties;
import net.sahibnanda.portfolio.config.SearchProperties;
import net.sahibnanda.portfolio.config.UserObserverProperties;
import net.sahibnanda.portfolio.dto.ChatObserverDTO;
import net.sahibnanda.portfolio.dto.UserObserverDTO;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.enums.OpenSearchExactQueryType;
import net.sahibnanda.portfolio.enums.OpenSearchFieldType;
import net.sahibnanda.portfolio.enums.OpenSearchFullTextQueryType;
import net.sahibnanda.portfolio.enums.OpenSearchQueryOperator;
import net.sahibnanda.portfolio.models.OpenSearchDocumentField;
import net.sahibnanda.portfolio.models.OpenSearchExactQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchFullTextQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchSearchHit;
import net.sahibnanda.portfolio.models.OpenSearchSearchResult;
import net.sahibnanda.portfolio.objects.ChatSearchResult;
import net.sahibnanda.portfolio.options.OpenSearchCreateIndexOptions;
import net.sahibnanda.portfolio.options.OpenSearchIndexDocumentOptions;
import net.sahibnanda.portfolio.options.OpenSearchSearchOptions;
import net.sahibnanda.portfolio.queue.Kafka;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Indexes chat lifecycle events into OpenSearch for full-text search. Consumes
 * {@link ChatObserverDTO} events published by
 * {@link net.sahibnanda.portfolio.repository.observers.ChatRepositoryObserver}
 * and upserts one OpenSearch document per chat title and per message, keyed so
 * later events for the same chat/message merge into the same document instead
 * of creating duplicates. Also consumes {@link UserObserverDTO} events
 * published by
 * {@link net.sahibnanda.portfolio.repository.observers.UserRepositoryObserver}
 * to remove a deleted user's chat documents -- chats are cascade-deleted from
 * Postgres when their owning user is deleted, so no per-chat
 * {@code CHAT_DELETED} event exists to trigger that cleanup otherwise.
 *
 * <p>
 * Document id scheme: a chat's title document is keyed {@code <chatId>#TITLE};
 * each message document is keyed
 * {@code <chatId>#<USER_MESSAGE|ASSISTANT_MESSAGE>#<epoch millis>}.
 */
@Slf4j
@Service
public class SearchService {

  /**
   * Document field name for the owning chat's id.
   */
  private static final String CHAT_ID_KEY = "chatId";

  /**
   * Document field name for the chat owner's username.
   */
  private static final String USERNAME_KEY = "username";

  /**
   * Document field name for which kind of document this is.
   */
  private static final String TYPE_KEY = "type";

  /**
   * Document field name for the indexed text (title or message content).
   */
  private static final String CONTENT_KEY = "content";

  /**
   * {@link #TYPE_KEY} value for a chat's title document.
   */
  private static final String TYPE_TITLE = "TITLE";

  /**
   * {@link #TYPE_KEY} value for a user-authored message document.
   */
  private static final String TYPE_USER_MESSAGE = "USER_MESSAGE";

  /**
   * {@link #TYPE_KEY} value for an assistant-authored message document.
   */
  private static final String TYPE_ASSISTANT_MESSAGE = "ASSISTANT_MESSAGE";

  /**
   * Retries per record given to {@link Kafka#startConsumer}.
   */
  private static final int CONSUMER_RETRY_COUNT = 3;

  /**
   * Upper bound on documents removed by one bulk delete (a chat's title plus
   * its messages, or every chat/message document for a deleted user). Capped at
   * OpenSearch's default {@code index.max_result_window} (10,000) -- requesting
   * a larger {@code size} makes every matching search request fail with
   * {@code search_phase_execution_exception}.
   */
  private static final int MAX_DELETE_BATCH_SIZE = 10_000;

  /**
   * Upper bound on chats returned by {@link #processUserQuery}.
   */
  private static final int SEARCH_RESULT_LIMIT = 20;

  /**
   * Full-match bonus for {@link #processUserQuery}'s should-clauses.
   */
  private static final float PHRASE_MATCH_BOOST = 8f;

  /**
   * Prefix-match bonus for {@link #processUserQuery}'s should-clauses.
   */
  private static final float PREFIX_MATCH_BOOST = 4f;

  /**
   * {@link #TYPE_TITLE} relevance boost.
   */
  private static final float TITLE_BOOST = 3f;

  /**
   * {@link #TYPE_USER_MESSAGE} relevance boost.
   */
  private static final float USER_MESSAGE_BOOST = 1.5f;

  /**
   * {@link #TYPE_ASSISTANT_MESSAGE} relevance boost.
   */
  private static final float ASSISTANT_MESSAGE_BOOST = 1.2f;

  /**
   * Mapping/document field for {@link #CHAT_ID_KEY}.
   */
  private static final OpenSearchDocumentField CHAT_ID_FIELD =
      OpenSearchDocumentField.indexed(CHAT_ID_KEY, OpenSearchFieldType.KEYWORD);

  /**
   * Mapping/document field for {@link #USERNAME_KEY}.
   */
  private static final OpenSearchDocumentField USERNAME_FIELD =
      OpenSearchDocumentField.indexed(USERNAME_KEY,
          OpenSearchFieldType.KEYWORD);

  /**
   * Mapping/document field for {@link #TYPE_KEY}.
   */
  private static final OpenSearchDocumentField TYPE_FIELD =
      OpenSearchDocumentField.indexed(TYPE_KEY, OpenSearchFieldType.KEYWORD);

  /**
   * Mapping/document field for {@link #CONTENT_KEY}.
   */
  private static final OpenSearchDocumentField CONTENT_FIELD =
      OpenSearchDocumentField.indexed(CONTENT_KEY, OpenSearchFieldType.TEXT);

  /**
   * Publishes to and consumes from Kafka.
   */
  private final Kafka kafka;

  /**
   * Indexes documents into OpenSearch.
   */
  private final OpenSearchAPI openSearchAPI;

  /**
   * The Kafka topics user lifecycle events are published to.
   */
  private final UserObserverProperties userObserverProperties;

  /**
   * The Kafka topics chat lifecycle events are published to.
   */
  private final ChatObserverProperties chatObserverProperties;

  /**
   * Index name and shard/replica settings for the chat search index.
   */
  private final SearchProperties searchProperties;

  /**
   * Creates a new search service and ensures the chat search index exists.
   *
   * @param kafkaClient publishes to and consumes from Kafka
   * @param openSearchClient indexes documents into OpenSearch
   * @param userProperties the Kafka topics user lifecycle events are published
   *        to
   * @param chatProperties the Kafka topics chat lifecycle events are published
   *        to
   * @param searchConfig index name and shard/replica settings for the chat
   *        search index
   */
  public SearchService(final Kafka kafkaClient,
      final OpenSearchAPI openSearchClient,
      final UserObserverProperties userProperties,
      final ChatObserverProperties chatProperties,
      final SearchProperties searchConfig) {
    this.kafka =
        Objects.requireNonNull(kafkaClient, "kafkaClient must not be null");
    this.openSearchAPI = Objects.requireNonNull(openSearchClient,
        "openSearchClient must not be null");
    this.userObserverProperties = Objects.requireNonNull(userProperties,
        "userProperties must not be null");
    this.chatObserverProperties = Objects.requireNonNull(chatProperties,
        "chatProperties must not be null");
    this.searchProperties =
        Objects.requireNonNull(searchConfig, "searchConfig must not be null");

    openSearchAPI.createIndexIfNotExists(OpenSearchCreateIndexOptions.builder()
        .indexName(searchProperties.chatIndexName())
        .numberOfShards(searchProperties.chatNumberOfShards())
        .numberOfReplicas(searchProperties.chatNumberOfReplicas())
        .mapping(CHAT_ID_FIELD).mapping(USERNAME_FIELD).mapping(TYPE_FIELD)
        .mapping(CONTENT_FIELD).build());
    log.debug("Ensured OpenSearch chat index exists: {}",
        searchProperties.chatIndexName());
  }

  /**
   * Subscribes to every configured chat-observer Kafka topic and indexes each
   * event into OpenSearch. Registered in {@code @PostConstruct} rather than the
   * constructor since it starts long-running consumer threads.
   */
  @PostConstruct
  public void startConsumerChat() {
    for (String topic : chatObserverProperties.kafkaTopics().keySet()) {
      kafka.startConsumer(topic, CONSUMER_RETRY_COUNT, ChatObserverDTO.class,
          (eventId, dto) -> handleChatEvent(dto));
    }
  }

  /**
   * Subscribes to every configured user-observer Kafka topic and reacts to each
   * event. Registered in {@code @PostConstruct} rather than the constructor
   * since it starts long-running consumer threads.
   */
  @PostConstruct
  public void startConsumerUser() {
    for (String topic : userObserverProperties.kafkaTopics().keySet()) {
      kafka.startConsumer(topic, CONSUMER_RETRY_COUNT, UserObserverDTO.class,
          (eventId, dto) -> handleUserEvent(dto));
    }
  }

  /**
   * Searches a user's chats, ranking full phrase matches highest, then prefix
   * matches, then any match containing the query terms, then typo-tolerant
   * matches -- with title documents boosted above user messages, boosted just
   * slightly above assistant messages. Each chat appears at most once,
   * represented by its single highest-scoring document. Scoped to the given
   * user's chats via {@link #findChatIdsOwnedBy}, not a direct field filter --
   * see that method for why. Uses distributed term frequency (an extra round
   * trip) so the type boosts above rank consistently -- with the chat index's
   * multiple shards, per-shard term statistics can otherwise score two
   * textually-identical documents differently enough to swamp a small, fixed
   * boost.
   *
   * @param username the chat owner to search within
   * @param query the search text
   * @return matching chats, highest score first, capped at
   *         {@link #SEARCH_RESULT_LIMIT}
   * @throws IllegalArgumentException if either argument is blank
   */
  public List<ChatSearchResult> processUserQuery(final String username,
      final String query) {
    if (StringUtils.isEmpty(username)) {
      throw new IllegalArgumentException("username must not be empty");
    }
    if (StringUtils.isEmpty(query)) {
      throw new IllegalArgumentException("query must not be empty");
    }
    String processedQuery = query.toLowerCase().trim();

    List<Object> chatIds = findChatIdsOwnedBy(username);
    if (chatIds.isEmpty()) {
      return List.of();
    }

    OpenSearchSearchResult result = openSearchAPI.search(OpenSearchSearchOptions
        .builder().indexName(searchProperties.chatIndexName())
        .filter(OpenSearchExactQueryCondition.builder()
            .type(OpenSearchExactQueryType.TERMS).field(CHAT_ID_KEY)
            .values(chatIds).build())
        .must(OpenSearchFullTextQueryCondition.builder()
            .type(OpenSearchFullTextQueryType.MATCH)
            .fields(List.of(CONTENT_KEY)).queryText(processedQuery)
            .operator(OpenSearchQueryOperator.OR).fuzziness("AUTO").build())
        .should(OpenSearchFullTextQueryCondition.builder()
            .type(OpenSearchFullTextQueryType.MATCH_PHRASE)
            .fields(List.of(CONTENT_KEY)).queryText(processedQuery)
            .boost(PHRASE_MATCH_BOOST).build())
        .should(OpenSearchFullTextQueryCondition.builder()
            .type(OpenSearchFullTextQueryType.MATCH_PHRASE_PREFIX)
            .fields(List.of(CONTENT_KEY)).queryText(processedQuery)
            .boost(PREFIX_MATCH_BOOST).build())
        .should(OpenSearchExactQueryCondition.builder()
            .type(OpenSearchExactQueryType.TERM).field(TYPE_KEY)
            .value(TYPE_TITLE).boost(TITLE_BOOST).build())
        .should(OpenSearchExactQueryCondition.builder()
            .type(OpenSearchExactQueryType.TERM).field(TYPE_KEY)
            .value(TYPE_USER_MESSAGE).boost(USER_MESSAGE_BOOST).build())
        .should(OpenSearchExactQueryCondition.builder()
            .type(OpenSearchExactQueryType.TERM).field(TYPE_KEY)
            .value(TYPE_ASSISTANT_MESSAGE).boost(ASSISTANT_MESSAGE_BOOST)
            .build())
        .collapseField(CHAT_ID_KEY).distributedTermFrequency(true)
        .size(SEARCH_RESULT_LIMIT).build());

    return result.getHits().stream()
        .map(hit -> ChatSearchResult.builder()
            .chatId((String) hit.getSource().get(CHAT_ID_KEY))
            .score(hit.getScore() == null ? 0.0 : hit.getScore()).build())
        .toList();
  }

  private void handleUserEvent(final UserObserverDTO dto) {
    if (dto == null) {
      throw new IllegalStateException("Received a null user observer event");
    }
    if (dto.getStatus() == null) {
      throw new IllegalStateException(
          "User observer event has no status: " + dto);
    }
    if (StringUtils.isEmpty(dto.getUsername())) {
      throw new IllegalStateException(
          "User observer event has no username: " + dto);
    }
    switch (dto.getStatus()) {
      case USER_DELETED -> handleUserDeletedEvent(dto);
      case USER_CREATED, USER_PASSWORD_UPDATED ->
        log.debug("No search index action for {} (user {})", dto.getStatus(),
            dto.getUsername());
      default ->
        log.warn("Unhandled user observer status: {}", dto.getStatus());
    }
  }

  /**
   * Deletes every OpenSearch document belonging to chats owned by a deleted
   * user -- their title and message documents. Chats are removed from Postgres
   * via an {@code ON DELETE CASCADE} foreign key when their owning user is
   * deleted, so no per-chat {@code CHAT_DELETED} event is published for them;
   * this is the only place that cleanup happens.
   *
   * @param dto the user deleted event
   */
  private void handleUserDeletedEvent(final UserObserverDTO dto) {
    List<Object> chatIds = findChatIdsOwnedBy(dto.getUsername());
    if (chatIds.isEmpty()) {
      return;
    }

    OpenSearchSearchResult chatDocuments =
        openSearchAPI.search(OpenSearchSearchOptions.builder()
            .indexName(searchProperties.chatIndexName())
            .filter(OpenSearchExactQueryCondition.builder()
                .type(OpenSearchExactQueryType.TERMS).field(CHAT_ID_KEY)
                .values(chatIds).build())
            .size(MAX_DELETE_BATCH_SIZE).build());

    chatDocuments.getHits().stream().map(OpenSearchSearchHit::getId)
        .forEach(documentId -> openSearchAPI.deleteDocumentIfExists(
            searchProperties.chatIndexName(), documentId));
  }

  /**
   * Finds the ids of every chat owned by a user, via their title documents --
   * the only document kind {@link #USERNAME_KEY} is reliably set on. Message
   * documents are indexed from
   * {@code CHAT_MESSAGE_SAVED_USER}/{@code _ASSISTANT} events, whose
   * {@link ChatObserverDTO#getUsername()} is always {@code null} by that DTO's
   * own documented design, so a direct {@link #USERNAME_KEY} lookup would miss
   * them.
   *
   * @param username the chat owner to look up
   * @return the owned chat ids, empty if the user owns no chats
   */
  private List<Object> findChatIdsOwnedBy(final String username) {
    OpenSearchSearchResult titleDocuments =
        openSearchAPI.search(OpenSearchSearchOptions.builder()
            .indexName(searchProperties.chatIndexName())
            .filter(OpenSearchExactQueryCondition.builder()
                .type(OpenSearchExactQueryType.TERM).field(USERNAME_KEY)
                .value(username).build())
            .filter(OpenSearchExactQueryCondition.builder()
                .type(OpenSearchExactQueryType.TERM).field(TYPE_KEY)
                .value(TYPE_TITLE).build())
            .size(MAX_DELETE_BATCH_SIZE).build());

    return titleDocuments.getHits().stream()
        .map(hit -> hit.getSource().get(CHAT_ID_KEY)).filter(Objects::nonNull)
        .toList();
  }

  private void handleChatEvent(final ChatObserverDTO dto) {
    if (dto == null) {
      throw new IllegalStateException("Received a null chat observer event");
    }
    if (dto.getStatus() == null) {
      throw new IllegalStateException(
          "Chat observer event has no status: " + dto);
    }
    if (StringUtils.isEmpty(dto.getChatId())) {
      throw new IllegalStateException(
          "Chat observer event has no chatId: " + dto);
    }
    switch (dto.getStatus()) {
      case CHAT_CREATED -> handleChatCreatedEvent(dto);
      case CHAT_TITLE_UPDATED -> handleChatTitleUpdatedEvent(dto);
      case CHAT_MESSAGE_SAVED_USER ->
        indexMessage(dto, Role.USER, TYPE_USER_MESSAGE);
      case CHAT_MESSAGE_SAVED_ASSISTANT ->
        indexMessage(dto, Role.ASSISTANT, TYPE_ASSISTANT_MESSAGE);
      case CHAT_DELETED -> handleChatDeletedEvent(dto);
      default ->
        log.warn("Unhandled chat observer status: {}", dto.getStatus());
    }
  }

  /**
   * Deletes every OpenSearch document belonging to a deleted chat -- its title
   * document and every message document.
   *
   * @param dto the chat deleted event
   */
  private void handleChatDeletedEvent(final ChatObserverDTO dto) {
    OpenSearchSearchResult result = openSearchAPI.search(OpenSearchSearchOptions
        .builder().indexName(searchProperties.chatIndexName())
        .filter(OpenSearchExactQueryCondition.builder()
            .type(OpenSearchExactQueryType.TERM).field(CHAT_ID_KEY)
            .value(dto.getChatId()).build())
        .size(MAX_DELETE_BATCH_SIZE).build());

    result.getHits().stream().map(OpenSearchSearchHit::getId)
        .forEach(documentId -> openSearchAPI.deleteDocumentIfExists(
            searchProperties.chatIndexName(), documentId));
  }

  private void handleChatCreatedEvent(final ChatObserverDTO dto) {
    openSearchAPI.upsertDocument(OpenSearchIndexDocumentOptions.builder()
        .indexName(searchProperties.chatIndexName())
        .documentId(titleDocumentId(dto.getChatId()))
        .document(CHAT_ID_FIELD, dto.getChatId())
        .document(USERNAME_FIELD, dto.getUsername())
        .document(TYPE_FIELD, TYPE_TITLE)
        .document(CONTENT_FIELD, dto.getChatTitle()).build());
  }

  /**
   * Updates a chat's title document. Only {@link #CONTENT_KEY} is set --
   * {@link #CHAT_ID_KEY}/{@link #USERNAME_KEY}/{@link #TYPE_KEY} never change
   * for a title document, and {@code upsertDocument} merges rather than
   * replaces, so the original values are left untouched.
   *
   * @param dto the chat title updated event
   */
  private void handleChatTitleUpdatedEvent(final ChatObserverDTO dto) {
    openSearchAPI.upsertDocument(OpenSearchIndexDocumentOptions.builder()
        .indexName(searchProperties.chatIndexName())
        .documentId(titleDocumentId(dto.getChatId()))
        .document(CONTENT_FIELD, dto.getChatTitle()).build());
  }

  /**
   * Indexes the most recently saved message of the given role.
   *
   * @param dto the message-saved event
   * @param role which author's message to index
   * @param type the {@link #TYPE_KEY} value to tag the document with
   * @throws IllegalStateException if {@code dto} carries no message of the
   *         given role
   */
  private void indexMessage(final ChatObserverDTO dto, final Role role,
      final String type) {
    Message message = mostRecentMessageByRole(dto, role);
    openSearchAPI.upsertDocument(OpenSearchIndexDocumentOptions.builder()
        .indexName(searchProperties.chatIndexName())
        .documentId(
            messageDocumentId(dto.getChatId(), type, message.timestamp()))
        .document(CHAT_ID_FIELD, dto.getChatId())
        .document(USERNAME_FIELD, dto.getUsername()).document(TYPE_FIELD, type)
        .document(CONTENT_FIELD, message.message()).build());
  }

  private Message mostRecentMessageByRole(final ChatObserverDTO dto,
      final Role role) {
    List<Message> messages = dto.getMessages();
    if (messages == null || messages.isEmpty()) {
      throw new IllegalStateException(
          "Expected messages on a message-saved event for chat "
              + dto.getChatId());
    }
    // Messages are stored oldest-first; the first role match after
    // reversing is the most recently saved message of that role. Null
    // elements are treated as malformed data, not a match, rather than
    // trusting every entry the consumer deserialized off Kafka.
    return messages.reversed().stream().filter(Objects::nonNull)
        .filter(candidate -> candidate.role() == role).findFirst()
        .orElseThrow(() -> new IllegalStateException("Expected a " + role
            + " message on a message-saved event for chat " + dto.getChatId()));
  }

  private String titleDocumentId(final String chatId) {
    return chatId + "#" + TYPE_TITLE;
  }

  private String messageDocumentId(final String chatId, final String type,
      final Instant timestamp) {
    return chatId + "#" + type + "#" + timestamp.toEpochMilli();
  }
}
