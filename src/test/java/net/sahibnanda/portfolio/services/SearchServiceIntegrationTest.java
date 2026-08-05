package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.api.OpenSearchAPI;
import net.sahibnanda.portfolio.config.ChatObserverProperties;
import net.sahibnanda.portfolio.config.SearchProperties;
import net.sahibnanda.portfolio.config.UserObserverProperties;
import net.sahibnanda.portfolio.dto.ChatObserverDTO;
import net.sahibnanda.portfolio.dto.UserObserverDTO;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.enums.ChatObserverStatus;
import net.sahibnanda.portfolio.enums.OpenSearchExactQueryType;
import net.sahibnanda.portfolio.enums.UserObserverStatus;
import net.sahibnanda.portfolio.models.OpenSearchExactQueryCondition;
import net.sahibnanda.portfolio.options.OpenSearchSearchOptions;
import net.sahibnanda.portfolio.queue.Kafka;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.repository.UserRepository;
import net.sahibnanda.portfolio.repository.jooq.JooqChatRepository;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SearchServiceIntegrationTest extends AbstractRepositoryIntegrationTest {

  // Wider than the single Kafka-round-trip case needs: the delete-then-
  // verify-absence tests chain a create, a delete, and a final poll, and
  // under real concurrent test execution (junit-platform.properties now
  // runs test classes in parallel) that chain can take noticeably longer
  // than it did when the whole suite ran strictly sequentially.
  private static final long POLL_TIMEOUT_MILLIS = 20_000;

  @Autowired
  private SearchService searchService;

  @Autowired
  private JooqChatRepository chatRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private OpenSearchAPI openSearchAPI;

  @Autowired
  private SearchProperties searchProperties;

  @Autowired
  private ChatObserverProperties chatObserverProperties;

  @Autowired
  private UserObserverProperties userObserverProperties;

  @Autowired
  private Kafka kafka;

  @BeforeEach
  void createOwningUser() {
    userRepository.create("alice", "hashed-pw");
  }

  @Test
  void creatingChatIndexesTitleDocument() {
    String chatId = StringUtils.generateUlid();

    chatRepository.create(chatId, "alice", "Trip to Kyoto", List.of());

    Map<String, Object> source = awaitDocument(chatId + "#TITLE");
    assertThat(source.get("content")).isEqualTo("Trip to Kyoto");
    assertThat(source.get("type")).isEqualTo("TITLE");
  }

  @Test
  void updatingChatTitleUpdatesTitleDocument() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Old Title", List.of());
    awaitDocument(chatId + "#TITLE");

    chatRepository.updateChatTitle(chatId, "New Title");

    Map<String, Object> source =
        awaitDocumentWithContent(chatId + "#TITLE", "New Title");
    assertThat(source.get("content")).isEqualTo("New Title");
  }

  @Test
  void savingUserMessageIndexesUserMessageDocument() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.saveMessages(chatId,
        List.of(new Message(Role.USER, "hello there", Instant.now())));

    Map<String, Object> source = awaitDocumentByType(chatId, "USER_MESSAGE");
    assertThat(source.get("content")).isEqualTo("hello there");
  }

  @Test
  void savingAssistantMessageIndexesAssistantMessageDocument() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.saveMessages(chatId, List
        .of(new Message(Role.ASSISTANT, "hi, how can I help?", Instant.now())));

    Map<String, Object> source =
        awaitDocumentByType(chatId, "ASSISTANT_MESSAGE");
    assertThat(source.get("content")).isEqualTo("hi, how can I help?");
  }

  @Test
  void malformedMessageSavedEventDoesNotCrashConsumer() {
    String badChatId = StringUtils.generateUlid();
    String goodChatId = StringUtils.generateUlid();
    chatRepository.create(badChatId, "alice", "Bad Chat", List.of());
    chatRepository.create(goodChatId, "alice", "Good Chat", List.of());
    String topic =
        chatObserverProperties.kafkaTopics().keySet().iterator().next();

    // A CHAT_MESSAGE_SAVED_USER event carrying only an assistant message --
    // SearchService must reject this defensively, not crash the consumer.
    kafka.sendEvent(topic, badChatId, ChatObserverDTO.builder()
        .chatId(badChatId).username("alice")
        .messages(List.of(new Message(Role.ASSISTANT, "oops", Instant.now())))
        .updatedAt(LocalDateTime.now())
        .status(ChatObserverStatus.CHAT_MESSAGE_SAVED_USER).build());

    chatRepository.saveMessages(goodChatId,
        List.of(new Message(Role.USER, "still works", Instant.now())));

    Map<String, Object> source =
        awaitDocumentByType(goodChatId, "USER_MESSAGE");
    assertThat(source.get("content")).isEqualTo("still works");
  }

  @Test
  void eventWithMissingChatIdDoesNotCrashConsumer() {
    String goodChatId = StringUtils.generateUlid();
    chatRepository.create(goodChatId, "alice", "Good Chat", List.of());
    String topic =
        chatObserverProperties.kafkaTopics().keySet().iterator().next();

    // No chatId at all -- SearchService must reject this defensively, not
    // crash the consumer thread trying to build a document id from it.
    kafka.sendEvent(topic, "no-chat-id-key", ChatObserverDTO.builder()
        .username("alice").status(ChatObserverStatus.CHAT_CREATED).build());

    chatRepository.updateChatTitle(goodChatId, "Still Works");

    Map<String, Object> source =
        awaitDocumentWithContent(goodChatId + "#TITLE", "Still Works");
    assertThat(source.get("content")).isEqualTo("Still Works");
  }

  @Test
  void deletingChatRemovesAllItsDocuments() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Trip to Kyoto", List.of());
    awaitDocument(chatId + "#TITLE");
    chatRepository.saveMessages(chatId,
        List.of(new Message(Role.USER, "hello there", Instant.now())));
    awaitDocumentByType(chatId, "USER_MESSAGE");

    chatRepository.delete(chatId);

    assertNoDocumentsForChat(chatId);
  }

  @Test
  void deletingUserRemovesAllItsChatDocuments() {
    String username = "user-" + StringUtils.generateUlid();
    userRepository.create(username, "hashed-pw");
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, username, "Trip to Kyoto", List.of());
    awaitDocument(chatId + "#TITLE");
    chatRepository.saveMessages(chatId,
        List.of(new Message(Role.USER, "hello there", Instant.now())));
    awaitDocumentByType(chatId, "USER_MESSAGE");

    userRepository.delete(username);

    assertNoDocumentsForChat(chatId);
  }

  @Test
  void userEventWithMissingUsernameDoesNotCrashConsumer() {
    String goodChatId = StringUtils.generateUlid();
    chatRepository.create(goodChatId, "alice", "Good Chat", List.of());
    String topic =
        userObserverProperties.kafkaTopics().keySet().iterator().next();

    // No username at all -- SearchService must reject this defensively, not
    // crash the consumer thread trying to build a delete query from it.
    kafka.sendEvent(topic, "no-username-key", UserObserverDTO.builder()
        .status(UserObserverStatus.USER_DELETED).build());

    chatRepository.updateChatTitle(goodChatId, "Still Works");

    Map<String, Object> source =
        awaitDocumentWithContent(goodChatId + "#TITLE", "Still Works");
    assertThat(source.get("content")).isEqualTo("Still Works");
  }

  private Map<String, Object> awaitDocument(final String documentId) {
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      var result = openSearchAPI.search(OpenSearchSearchOptions.builder()
          .indexName(searchProperties.chatIndexName())
          .filter(OpenSearchExactQueryCondition.builder()
              .type(OpenSearchExactQueryType.IDS).values(List.of(documentId))
              .build())
          .build());
      if (!result.getHits().isEmpty()) {
        return result.getHits().get(0).getSource();
      }
    }
    throw new AssertionError(
        "No document " + documentId + " indexed within timeout");
  }

  private Map<String, Object> awaitDocumentWithContent(final String documentId,
      final String expectedContent) {
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      Map<String, Object> source = awaitDocument(documentId);
      if (expectedContent.equals(source.get("content"))) {
        return source;
      }
    }
    throw new AssertionError("Document " + documentId + " never showed content "
        + expectedContent + " within timeout");
  }

  private Map<String, Object> awaitDocumentByType(final String chatId,
      final String type) {
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      var result = openSearchAPI.search(OpenSearchSearchOptions.builder()
          .indexName(searchProperties.chatIndexName())
          .filter(OpenSearchExactQueryCondition.builder()
              .type(OpenSearchExactQueryType.TERM).field("chatId").value(chatId)
              .build())
          .filter(OpenSearchExactQueryCondition.builder()
              .type(OpenSearchExactQueryType.TERM).field("type").value(type)
              .build())
          .build());
      if (!result.getHits().isEmpty()) {
        return result.getHits().get(0).getSource();
      }
    }
    throw new AssertionError("No " + type + " document indexed for chat "
        + chatId + " within timeout");
  }

  private void assertNoDocumentsForChat(final String chatId) {
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      var result = openSearchAPI.search(OpenSearchSearchOptions.builder()
          .indexName(searchProperties.chatIndexName())
          .filter(OpenSearchExactQueryCondition.builder()
              .type(OpenSearchExactQueryType.TERM).field("chatId").value(chatId)
              .build())
          .build());
      if (result.getHits().isEmpty()) {
        return;
      }
    }
    throw new AssertionError(
        "Documents for chat " + chatId + " still present after timeout");
  }
}
