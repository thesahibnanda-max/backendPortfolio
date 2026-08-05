package net.sahibnanda.portfolio.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.ulid.UlidCreator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.sahibnanda.portfolio.exception.KafkaConsumerAlreadyStartedException;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
class KafkaTest {

  @Container
  private static final KafkaContainer KAFKA =
      new KafkaContainer("apache/kafka:4.1.0");

  private static String bootstrapServers;

  private static ProducerFactory<String, Object> producerFactory;

  private Kafka kafka;

  private String topicName;

  @BeforeAll
  static void setupProducer() {
    bootstrapServers = KAFKA.getBootstrapServers();
    producerFactory = new DefaultKafkaProducerFactory<>(Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class));
  }

  // Each test gets its own consumer-group id (not just its own topic) so a
  // still-departing consumer from one test can never trigger a rebalance
  // that stalls the next test's brand-new, otherwise-solo group.
  @BeforeEach
  void createTopicAndKafka() {
    topicName = "kafka-test-" + UlidCreator.getUlid();

    JsonDeserializer<Object> valueDeserializer =
        new JsonDeserializer<>(Object.class);
    valueDeserializer.addTrustedPackages("*");
    ConsumerFactory<String, Object> consumerFactory =
        new DefaultKafkaConsumerFactory<>(
            Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,
                "kafka-test-" + UlidCreator.getUlid(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
            new StringDeserializer(), valueDeserializer);

    kafka = new Kafka(
        new KafkaTemplate<>(producerFactory), new KafkaAdmin(Map
            .of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)),
        consumerFactory);
    kafka.createTopicIfNotExists(topicName, 1, (short) 1);
  }

  @AfterEach
  void cleanup() {
    kafka.shutdownConsumerGracefully();
    kafka.deleteTopicIfExists(topicName);
  }

  @Test
  void producedMessageIsConsumedAndCommitted() throws InterruptedException {
    BlockingQueue<Map> received = new LinkedBlockingQueue<>();
    kafka.startConsumer(topicName, 0, Map.class,
        (key, value) -> received.add(value));

    kafka.sendEvent(topicName, "event-1", Map.of("hello", "world"));

    Map<?, ?> message = received.poll(10, TimeUnit.SECONDS);
    assertThat(message).isNotNull();
  }

  @Test
  void retriesFailingHandlerUpToRetryCountThenSucceeds()
      throws InterruptedException {
    AtomicInteger attempts = new AtomicInteger(0);
    BlockingQueue<Map> received = new LinkedBlockingQueue<>();
    kafka.startConsumer(topicName, 3, Map.class, (key, value) -> {
      if (attempts.incrementAndGet() < 3) {
        throw new RuntimeException("simulated failure");
      }
      received.add(value);
    });

    kafka.sendEvent(topicName, "event-1", Map.of("hello", "world"));

    Map<?, ?> message = received.poll(10, TimeUnit.SECONDS);
    assertThat(message).isNotNull();
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void continuesConsumingAfterExhaustingRetriesOnABadRecord()
      throws InterruptedException {
    BlockingQueue<String> received = new LinkedBlockingQueue<>();
    kafka.startConsumer(topicName, 1, Object.class, (key, value) -> {
      if ("bad".equals(key)) {
        throw new RuntimeException("always fails");
      }
      received.add(key);
    });

    kafka.sendEvent(topicName, "bad", Map.of("poison", true));
    kafka.sendEvent(topicName, "good", Map.of("ok", true));

    String key = received.poll(10, TimeUnit.SECONDS);
    assertThat(key).isEqualTo("good");
  }

  @Test
  void pingSucceedsAgainstARealBroker() {
    assertThatCode(kafka::ping).doesNotThrowAnyException();
  }

  @Test
  void createTopicIfNotExistsIsIdempotent() {
    assertThatCode(() -> kafka.createTopicIfNotExists(topicName, 1, (short) 1))
        .doesNotThrowAnyException();
  }

  @Test
  void deleteTopicIfExistsIsIdempotentForMissingTopic() {
    String missingTopic = "kafka-test-missing-" + UlidCreator.getUlid();

    assertThatCode(() -> kafka.deleteTopicIfExists(missingTopic))
        .doesNotThrowAnyException();
  }

  @Test
  void startingConsumerTwiceForSameTopicThrows() {
    kafka.startConsumer(topicName, 0, Object.class, (key, value) -> {
    });

    assertThatThrownBy(
        () -> kafka.startConsumer(topicName, 0, Object.class, (key, value) -> {
        })).isInstanceOf(KafkaConsumerAlreadyStartedException.class);
  }
}
