package net.sahibnanda.portfolio.repository.observers;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.ulid.UlidCreator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.config.ChatObserverProperties;
import net.sahibnanda.portfolio.dto.ChatObserverDTO;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.enums.ChatObserverStatus;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.repository.UserRepository;
import net.sahibnanda.portfolio.repository.jooq.JooqChatRepository;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.kafka.support.serializer.JsonDeserializer;

class ChatRepositoryObserverIntegrationTest
    extends AbstractRepositoryIntegrationTest {

  @Autowired
  private JooqChatRepository chatRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private KafkaConnectionDetails kafkaConnectionDetails;

  @Autowired
  private ChatObserverProperties chatObserverProperties;

  @BeforeEach
  void createOwningUser() {
    userRepository.create("alice", "hashed-pw");
  }

  @Test
  void creatingChatPublishesChatCreatedEvent() {
    String chatId = StringUtils.generateUlid();

    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    ChatObserverDTO event = awaitEvent(chatId, ChatObserverStatus.CHAT_CREATED);
    assertThat(event.getUsername()).isEqualTo("alice");
    assertThat(event.getChatTitle()).isEqualTo("Chat 1");
  }

  @Test
  void deletingChatPublishesChatDeletedEvent() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.delete(chatId);

    // A created-then-deleted chat has two events under the same key, so
    // the awaited status must be matched explicitly, not just the key.
    awaitEvent(chatId, ChatObserverStatus.CHAT_DELETED);
  }

  @Test
  void savingUserMessagePublishesChatMessageSavedUserEvent() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.saveMessages(chatId,
        List.of(new Message(Role.USER, "hi", Instant.now())));

    ChatObserverDTO event =
        awaitEvent(chatId, ChatObserverStatus.CHAT_MESSAGE_SAVED_USER);
    assertThat(event.getChatId()).isEqualTo(chatId);
    assertThat(event.getUpdatedAt()).isNotNull();
  }

  @Test
  void savingAssistantMessagePublishesChatMessageSavedAssistantEvent() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.saveMessages(chatId,
        List.of(new Message(Role.ASSISTANT, "hello", Instant.now())));

    ChatObserverDTO event =
        awaitEvent(chatId, ChatObserverStatus.CHAT_MESSAGE_SAVED_ASSISTANT);
    assertThat(event.getChatId()).isEqualTo(chatId);
    assertThat(event.getUpdatedAt()).isNotNull();
  }

  @Test
  void savingMessagesDerivesEventFromMostRecentMessage() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());
    Message olderAssistant = new Message(Role.ASSISTANT, "older",
        Instant.parse("2026-01-01T00:00:00Z"));
    Message newerUser =
        new Message(Role.USER, "newer", Instant.parse("2026-01-02T00:00:00Z"));

    // Inserted out of chronological order -- the derived status must
    // follow the timestamp, not list position.
    chatRepository.saveMessages(chatId, List.of(newerUser, olderAssistant));

    awaitEvent(chatId, ChatObserverStatus.CHAT_MESSAGE_SAVED_USER);
  }

  @Test
  void savingEmptyMessagesListPublishesNoMessageSavedEvent() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.saveMessages(chatId, List.of());

    assertNoMessageSavedEvent(chatId);
  }

  private ChatObserverDTO awaitEvent(final String chatId,
      final ChatObserverStatus expectedStatus) {
    try (KafkaConsumer<String, ChatObserverDTO> consumer = newConsumer()) {
      consumer.subscribe(List.of(topic()));
      long deadline = System.currentTimeMillis() + 10_000;
      while (System.currentTimeMillis() < deadline) {
        ConsumerRecords<String, ChatObserverDTO> records =
            consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, ChatObserverDTO> record : records) {
          if (chatId.equals(record.key())
              && record.value().getStatus() == expectedStatus) {
            return record.value();
          }
        }
      }
    }
    throw new AssertionError("No " + expectedStatus + " event received for "
        + "chat " + chatId + " within timeout");
  }

  /**
   * Polls for a short window and fails if a {@code CHAT_MESSAGE_SAVED_USER} or
   * {@code CHAT_MESSAGE_SAVED_ASSISTANT} event for {@code chatId} shows up --
   * used to prove an empty message list doesn't publish either event.
   *
   * @param chatId the chat expected to receive no message-saved event
   */
  private void assertNoMessageSavedEvent(final String chatId) {
    try (KafkaConsumer<String, ChatObserverDTO> consumer = newConsumer()) {
      consumer.subscribe(List.of(topic()));
      long deadline = System.currentTimeMillis() + 3_000;
      while (System.currentTimeMillis() < deadline) {
        ConsumerRecords<String, ChatObserverDTO> records =
            consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, ChatObserverDTO> record : records) {
          ChatObserverStatus status = record.value().getStatus();
          if (chatId.equals(record.key())
              && (status == ChatObserverStatus.CHAT_MESSAGE_SAVED_USER
                  || status == ChatObserverStatus.CHAT_MESSAGE_SAVED_ASSISTANT)) {
            throw new AssertionError(
                "Unexpected " + status + " event received for chat " + chatId);
          }
        }
      }
    }
  }

  private KafkaConsumer<String, ChatObserverDTO> newConsumer() {
    JsonDeserializer<ChatObserverDTO> valueDeserializer =
        new JsonDeserializer<>(ChatObserverDTO.class);
    valueDeserializer.addTrustedPackages("*");
    Map<String, Object> props = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        String.join(",", kafkaConnectionDetails.getBootstrapServers()),
        ConsumerConfig.GROUP_ID_CONFIG,
        "chat-observer-test-" + UlidCreator.getUlid(),
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(props, new StringDeserializer(),
        valueDeserializer);
  }

  private String topic() {
    return chatObserverProperties.kafkaTopics().keySet().iterator().next();
  }
}
