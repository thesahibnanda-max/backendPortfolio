package net.sahibnanda.portfolio.queue;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.exception.KafkaConsumerAlreadyStartedException;
import net.sahibnanda.portfolio.exception.KafkaOperationException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes events to Kafka and manages dynamically-started per-topic
 * consumers. Each consumer owns a dedicated {@link Consumer} on its own virtual
 * thread, commits offsets only after successful processing, retries a failing
 * handler up to a caller-supplied count, and respawns automatically if its
 * thread dies unexpectedly.
 */
@Slf4j
@Component
public final class Kafka {

  /** Bound on every blocking admin/producer call. */
  private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(10);

  /** How long each consumer loop iteration blocks waiting for records. */
  private static final Duration POLL_DURATION = Duration.ofSeconds(2);

  /** Pause between retry attempts on a failing record. */
  private static final Duration RETRY_BACKOFF = Duration.ofMillis(500);

  /** Pause before respawning a consumer whose thread died unexpectedly. */
  private static final Duration RESPAWN_BACKOFF = Duration.ofSeconds(2);

  /** Bound on waiting for in-flight consumer loops to exit on shutdown. */
  private static final Duration EXECUTOR_SHUTDOWN_TIMEOUT =
      Duration.ofSeconds(30);

  /** Publishes events. */
  private final KafkaTemplate<String, Object> kafkaTemplate;

  /** Manages topics. */
  private final Admin admin;

  /** Builds a fresh, independent consumer per topic. */
  private final ConsumerFactory<String, Object> consumerFactory;

  /** Runs every consumer poll loop, one virtual thread per topic. */
  private final ExecutorService virtualThreadExecutor =
      Executors.newVirtualThreadPerTaskExecutor();

  /** Active consumers, keyed by topic name. */
  private final Map<String, ConsumerHandle> startedConsumers =
      new ConcurrentHashMap<>();

  /**
   * A running consumer and the flag used to signal it to stop.
   *
   * @param consumer the raw client this handle owns
   * @param stopped set before {@link Consumer#wakeup()} to distinguish a
   *        deliberate shutdown from an unexpected failure
   */
  private record ConsumerHandle(Consumer<String, Object> consumer,
      AtomicBoolean stopped) {
  }

  /**
   * Creates a new Kafka producer/consumer component.
   *
   * @param kafkaTemplateClient publishes events
   * @param kafkaAdmin supplies the admin client's connection config
   * @param consumerFactoryClient builds a fresh consumer per topic
   */
  public Kafka(final KafkaTemplate<String, Object> kafkaTemplateClient,
      final KafkaAdmin kafkaAdmin,
      final ConsumerFactory<String, Object> consumerFactoryClient) {
    this.kafkaTemplate = Objects.requireNonNull(kafkaTemplateClient,
        "kafkaTemplateClient must not be null");
    this.admin = Admin.create(
        Objects.requireNonNull(kafkaAdmin, "kafkaAdmin must not be null")
            .getConfigurationProperties());
    this.consumerFactory = Objects.requireNonNull(consumerFactoryClient,
        "consumerFactoryClient must not be null");
  }

  /**
   * Publishes an event.
   *
   * @param topicName the topic to publish to
   * @param eventId the record key
   * @param event the event payload
   * @throws KafkaOperationException if the publish fails or times out
   */
  public void sendEvent(final String topicName, final String eventId,
      final Object event) {
    await(kafkaTemplate.send(topicName, eventId, event));
  }

  /**
   * Checks Kafka connectivity by asking the admin client to describe the
   * cluster -- no topic or consumer involved.
   *
   * @throws KafkaOperationException if the cluster is unreachable or the call
   *         times out
   */
  public void ping() {
    await(admin.describeCluster().nodes());
  }

  /**
   * Creates a topic if it doesn't already exist.
   *
   * @param topicName the topic to create
   * @param partitionCount number of partitions
   * @param replicationFactor replication factor
   * @throws KafkaOperationException if the admin call fails or times out
   */
  public void createTopicIfNotExists(final String topicName,
      final int partitionCount, final short replicationFactor) {
    Set<String> topics = await(admin.listTopics().names());
    if (topics.contains(topicName)) {
      return;
    }
    await(admin
        .createTopics(
            List.of(new NewTopic(topicName, partitionCount, replicationFactor)))
        .all());
  }

  /**
   * Deletes a topic if it exists.
   *
   * @param topicName the topic to delete
   * @throws KafkaOperationException if the admin call fails or times out
   */
  public void deleteTopicIfExists(final String topicName) {
    Set<String> topics = await(admin.listTopics().names());
    if (!topics.contains(topicName)) {
      return;
    }
    await(admin.deleteTopics(List.of(topicName)).all());
    stopConsumer(topicName);
  }

  /**
   * Starts a resilient consumer for {@code topicName}: a dedicated consumer on
   * its own virtual thread that commits an offset only after
   * {@code messageHandler} succeeds, retries a failing record up to
   * {@code retryCount} times before giving up on it, and respawns itself if the
   * thread dies unexpectedly.
   *
   * @param <T> the deserialized value type
   * @param topicName the topic to consume
   * @param retryCount how many extra attempts a failing record gets before it's
   *        logged at ERROR and committed anyway, so one bad record can never
   *        block the partition forever
   * @param valueType the type each record's value is cast to before
   *        {@code messageHandler} receives it
   * @param messageHandler receives each record's key and typed value
   * @throws IllegalArgumentException if {@code retryCount} is negative
   * @throws KafkaConsumerAlreadyStartedException if a consumer for this topic
   *         is already running
   */
  public <T> void startConsumer(final String topicName, final int retryCount,
      final Class<T> valueType, final BiConsumer<String, T> messageHandler) {
    Objects.requireNonNull(valueType, "valueType must not be null");
    Objects.requireNonNull(messageHandler, "messageHandler must not be null");
    if (retryCount < 0) {
      throw new IllegalArgumentException("retryCount must not be negative");
    }
    if (startedConsumers.containsKey(topicName)) {
      throw new KafkaConsumerAlreadyStartedException(
          "Consumer already started for topic: " + topicName);
    }
    launchConsumerLoop(topicName, retryCount,
        (key, value) -> messageHandler.accept(key, valueType.cast(value)));
  }

  /**
   * Stops the consumer for {@code topicName}, if one is running. Only wakes it
   * up here -- the owning virtual thread in {@link #runConsumerLoop} closes it,
   * since {@code Consumer#close()} is not safe to call from a different thread
   * than the one polling.
   *
   * @param topicName the topic whose consumer should stop
   */
  @SuppressWarnings("resource")
  public void stopConsumer(final String topicName) {
    ConsumerHandle handle = startedConsumers.remove(topicName);
    if (handle != null) {
      handle.stopped().set(true);
      handle.consumer().wakeup();
    }
  }

  /**
   * Stops every currently-running consumer.
   */
  public void shutdownConsumerGracefully() {
    new ArrayList<>(startedConsumers.keySet()).forEach(this::stopConsumer);
  }

  /**
   * Stops every consumer, waits for their threads to exit, then closes the
   * admin client.
   */
  @PreDestroy
  void close() {
    shutdownConsumerGracefully();
    virtualThreadExecutor.shutdown();
    awaitExecutorTermination();
    admin.close();
  }

  private void awaitExecutorTermination() {
    try {
      if (!virtualThreadExecutor.awaitTermination(
          EXECUTOR_SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
        virtualThreadExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      virtualThreadExecutor.shutdownNow();
    }
  }

  private void launchConsumerLoop(final String topicName, final int retryCount,
      final BiConsumer<String, Object> messageHandler) {
    Consumer<String, Object> consumer = consumerFactory.createConsumer();
    AtomicBoolean stopped = new AtomicBoolean(false);
    startedConsumers.put(topicName, new ConsumerHandle(consumer, stopped));
    virtualThreadExecutor.submit(() -> runConsumerLoop(topicName, retryCount,
        messageHandler, consumer, stopped));
  }

  private void runConsumerLoop(final String topicName, final int retryCount,
      final BiConsumer<String, Object> messageHandler,
      final Consumer<String, Object> consumer, final AtomicBoolean stopped) {
    try (consumer) {
      consumer.subscribe(List.of(topicName));
      while (!stopped.get()) {
        ConsumerRecords<String, Object> records = consumer.poll(POLL_DURATION);
        for (ConsumerRecord<String, Object> record : records) {
          processRecord(record, messageHandler, retryCount, consumer,
              topicName);
        }
      }
    } catch (WakeupException e) {
      if (!stopped.get()) {
        log.error("Consumer for topic {} woken unexpectedly; respawning",
            topicName, e);
        respawn(topicName, retryCount, messageHandler, consumer, stopped);
      }
    } catch (RuntimeException e) {
      log.error("Consumer loop for topic {} died unexpectedly; respawning "
          + "after backoff", topicName, e);
      respawn(topicName, retryCount, messageHandler, consumer, stopped);
    }
  }

  private void respawn(final String topicName, final int retryCount,
      final BiConsumer<String, Object> messageHandler,
      final Consumer<String, Object> deadConsumer,
      final AtomicBoolean stopped) {
    if (stopped.get()) {
      return;
    }
    if (!sleepQuietly(RESPAWN_BACKOFF)) {
      return;
    }
    if (stopped.get()) {
      return;
    }
    // Only relaunch if nobody called stopConsumer() during the backoff --
    // remove(key, value) is a no-op if the tracked handle already changed.
    ConsumerHandle staleHandle = new ConsumerHandle(deadConsumer, stopped);
    if (startedConsumers.remove(topicName, staleHandle)) {
      launchConsumerLoop(topicName, retryCount, messageHandler);
    }
  }

  private void processRecord(final ConsumerRecord<String, Object> record,
      final BiConsumer<String, Object> messageHandler, final int retryCount,
      final Consumer<String, Object> consumer, final String topicName) {
    int attempt = 0;
    while (true) {
      try {
        messageHandler.accept(record.key(), record.value());
        commitRecord(consumer, record);
        return;
      } catch (RuntimeException e) {
        attempt++;
        log.warn("Handler failed for topic {} offset {} (attempt {}/{})",
            topicName, record.offset(), attempt, retryCount + 1, e);
        if (attempt > retryCount) {
          log.error(
              "Exhausted {} attempts for topic {} offset {}; "
                  + "committing anyway so the partition isn't blocked",
              attempt, topicName, record.offset(), e);
          commitRecord(consumer, record);
          return;
        }
        if (!sleepQuietly(RETRY_BACKOFF)) {
          return;
        }
      }
    }
  }

  private void commitRecord(final Consumer<String, Object> consumer,
      final ConsumerRecord<String, Object> record) {
    consumer.commitSync(
        Map.of(new TopicPartition(record.topic(), record.partition()),
            new OffsetAndMetadata(record.offset() + 1)));
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean sleepQuietly(final Duration duration) {
    try {
      Thread.sleep(duration);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @CanIgnoreReturnValue
  private <T> T await(final CompletableFuture<T> future) {
    try {
      return future.get(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new KafkaOperationException("Interrupted while waiting on Kafka.",
          e);
    } catch (ExecutionException | TimeoutException e) {
      throw new KafkaOperationException("Kafka operation failed.", e);
    }
  }

  private <T> T await(final KafkaFuture<T> future) {
    try {
      return future.get(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new KafkaOperationException("Interrupted while waiting on Kafka.",
          e);
    } catch (ExecutionException | TimeoutException e) {
      throw new KafkaOperationException("Kafka operation failed.", e);
    }
  }
}
