package net.sahibnanda.portfolio.repository.observers;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.ulid.UlidCreator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.config.UserObserverProperties;
import net.sahibnanda.portfolio.dto.UserObserverDTO;
import net.sahibnanda.portfolio.enums.UserObserverStatus;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.repository.jooq.JooqUserRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

class UserRepositoryObserverIntegrationTest
    extends AbstractRepositoryIntegrationTest {

  @Autowired
  private JooqUserRepository userRepository;

  @Autowired
  private KafkaProperties kafkaProperties;

  @Autowired
  private UserObserverProperties userObserverProperties;

  @Test
  void creatingUserPublishesUserCreatedEvent() {
    String username = ("user-" + UlidCreator.getUlid()).toLowerCase();

    userRepository.create(username, "hashed-pw");

    UserObserverDTO event =
        awaitEvent(username, UserObserverStatus.USER_CREATED);
    assertThat(event.getUsername()).isEqualTo(username);
    assertThat(event.getCreatedAt()).isNotNull();
  }

  @Test
  void updatingPasswordPublishesUserPasswordUpdatedEvent() {
    String username = ("user-" + UlidCreator.getUlid()).toLowerCase();
    userRepository.create(username, "hashed-pw");

    userRepository.updatePassword(username, "new-hashed-pw");

    awaitEvent(username, UserObserverStatus.USER_PASSWORD_UPDATED);
  }

  @Test
  void deletingUserPublishesUserDeletedEvent() {
    String username = ("user-" + UlidCreator.getUlid()).toLowerCase();
    userRepository.create(username, "hashed-pw");

    userRepository.delete(username);

    awaitEvent(username, UserObserverStatus.USER_DELETED);
  }

  private UserObserverDTO awaitEvent(final String username,
      final UserObserverStatus expectedStatus) {
    JsonDeserializer<UserObserverDTO> valueDeserializer =
        new JsonDeserializer<>(UserObserverDTO.class);
    valueDeserializer.addTrustedPackages("*");
    Map<String, Object> props = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        String.join(",", kafkaProperties.getBootstrapServers()),
        ConsumerConfig.GROUP_ID_CONFIG,
        "user-observer-test-" + UlidCreator.getUlid(),
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    String topic =
        userObserverProperties.kafkaTopics().keySet().iterator().next();

    try (KafkaConsumer<String, UserObserverDTO> consumer = new KafkaConsumer<>(
        props, new StringDeserializer(), valueDeserializer)) {
      consumer.subscribe(List.of(topic));
      long deadline = System.currentTimeMillis() + 10_000;
      while (System.currentTimeMillis() < deadline) {
        ConsumerRecords<String, UserObserverDTO> records =
            consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, UserObserverDTO> record : records) {
          if (username.equals(record.key())
              && record.value().getStatus() == expectedStatus) {
            return record.value();
          }
        }
      }
    }
    throw new AssertionError("No " + expectedStatus + " event received for "
        + "user " + username + " within timeout");
  }
}
