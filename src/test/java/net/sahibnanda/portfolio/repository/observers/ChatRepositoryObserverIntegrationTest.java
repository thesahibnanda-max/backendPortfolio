package net.sahibnanda.portfolio.repository.observers;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.ulid.UlidCreator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.dto.ChatObserverDTO;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;

class ChatRepositoryObserverIntegrationTest
    extends AbstractRepositoryIntegrationTest {

  private static final String TOPIC = "chat_repo_event";

  private static final String BOOTSTRAP_SERVERS = "localhost:9092";

  @Autowired
  private JooqChatRepository chatRepository;

  @Autowired
  private UserRepository userRepository;

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

  private ChatObserverDTO awaitEvent(final String chatId,
      final ChatObserverStatus expectedStatus) {
    JsonDeserializer<ChatObserverDTO> valueDeserializer =
        new JsonDeserializer<>(ChatObserverDTO.class);
    valueDeserializer.addTrustedPackages("*");
    Map<String, Object> props = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        BOOTSTRAP_SERVERS, ConsumerConfig.GROUP_ID_CONFIG,
        "chat-observer-test-" + UlidCreator.getUlid(),
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    try (KafkaConsumer<String, ChatObserverDTO> consumer = new KafkaConsumer<>(
        props, new StringDeserializer(), valueDeserializer)) {
      consumer.subscribe(List.of(TOPIC));
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
}
