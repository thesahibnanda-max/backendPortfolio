package net.sahibnanda.portfolio.repository.observers;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.ChatObserverProperties;
import net.sahibnanda.portfolio.dto.ChatObserverDTO;
import net.sahibnanda.portfolio.queue.Kafka;
import org.springframework.stereotype.Component;

/**
 * Notifies every registered observer subject whenever a chat is created,
 * updated, or deleted. Each notification is dispatched on its own
 * fire-and-forget virtual thread -- callers never wait for it.
 */
@Slf4j
@Component
public final class ChatRepositoryObserver {

  /** Every subject notified on each chat event. */
  private final List<ISubject> subjects;

  /**
   * Creates a new chat repository observer, wiring up every subject and running
   * each subject's one-time setup.
   *
   * @param kafka publishes chat events to Kafka
   * @param chatObserverProperties the Kafka topics each event is published to
   */
  public ChatRepositoryObserver(final Kafka kafka,
      final ChatObserverProperties chatObserverProperties) {
    subjects = List.of(new KafkaSubject(kafka, chatObserverProperties));
    subjects.forEach(ISubject::setupObserver);
  }

  /**
   * Notifies every subject of a chat event. Fire-and-forget: each subject is
   * notified on its own virtual thread, and a failure is logged but never
   * thrown back to the caller.
   *
   * @param dto the chat event to publish
   */
  public void notifyAllObservers(final ChatObserverDTO dto) {
    subjects.forEach(subject -> Thread.ofVirtual().start(() -> {
      try {
        subject.notifyObservers(dto);
      } catch (RuntimeException e) {
        log.error("Observer notification failed for chat {}", dto.getChatId(),
            e);
      }
    }));
  }

  /**
   * A single observer subject: something notified of every chat event.
   */
  private interface ISubject {

    /**
     * Runs any one-time setup this subject needs before it can be notified
     * (e.g. provisioning a Kafka topic).
     */
    void setupObserver();

    /**
     * Notifies this subject of a chat event.
     *
     * @param dto the chat event
     */
    void notifyObservers(ChatObserverDTO dto);
  }

  /**
   * Publishes chat events to every configured Kafka topic.
   *
   * @param kafka publishes events and provisions topics
   * @param properties the Kafka topics each event is published to
   */
  private record KafkaSubject(Kafka kafka,
      ChatObserverProperties properties) implements ISubject {

    private KafkaSubject {
      Objects.requireNonNull(kafka, "kafka is null");
      Objects.requireNonNull(properties, "properties is null");
    }

    @Override
    public void notifyObservers(final ChatObserverDTO dto) {
      properties.kafkaTopics().keySet()
          .forEach(topic -> kafka.sendEvent(topic, dto.getChatId(), dto));
    }

    @Override
    public void setupObserver() {
      properties.kafkaTopics()
          .forEach((topic, config) -> kafka.createTopicIfNotExists(topic,
              config.partitions(), config.replicationFactor()));
    }
  }
}
