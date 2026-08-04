package net.sahibnanda.portfolio.repository.observers;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.UserObserverProperties;
import net.sahibnanda.portfolio.dto.UserObserverDTO;
import net.sahibnanda.portfolio.queue.Kafka;
import org.springframework.stereotype.Component;

/**
 * Notifies every registered observer subject whenever a user is created, has
 * its password updated, or is deleted. Each notification is dispatched on its
 * own fire-and-forget virtual thread -- callers never wait for it.
 */
@Slf4j
@Component
public final class UserRepositoryObserver {

  /** Every subject notified on each user event. */
  private final List<ISubject> subjects;

  /**
   * Creates a new user repository observer, wiring up every subject and running
   * each subject's one-time setup.
   *
   * @param kafka publishes user events to Kafka
   * @param userObserverProperties the Kafka topics each event is published to
   */
  public UserRepositoryObserver(final Kafka kafka,
      final UserObserverProperties userObserverProperties) {
    subjects = List.of(new KafkaSubject(kafka, userObserverProperties));
    subjects.forEach(ISubject::setupObserver);
  }

  /**
   * Notifies every subject of a user event. Fire-and-forget: each subject is
   * notified on its own virtual thread, and a failure is logged but never
   * thrown back to the caller.
   *
   * @param dto the user event to publish
   */
  public void notifyAllObservers(final UserObserverDTO dto) {
    subjects.forEach(subject -> Thread.ofVirtual().start(() -> {
      try {
        subject.notifyObservers(dto);
      } catch (RuntimeException e) {
        log.error("Observer notification failed for user {}", dto.getUsername(),
            e);
      }
    }));
  }

  /**
   * A single observer subject: something notified of every user event.
   */
  private interface ISubject {

    /**
     * Runs any one-time setup this subject needs before it can be notified
     * (e.g. provisioning a Kafka topic).
     */
    void setupObserver();

    /**
     * Notifies this subject of a user event.
     *
     * @param dto the user event
     */
    void notifyObservers(UserObserverDTO dto);
  }

  /**
   * Publishes user events to every configured Kafka topic.
   *
   * @param kafka publishes events and provisions topics
   * @param properties the Kafka topics each event is published to
   */
  private record KafkaSubject(Kafka kafka,
      UserObserverProperties properties) implements ISubject {

    private KafkaSubject {
      Objects.requireNonNull(kafka, "kafka is null");
      Objects.requireNonNull(properties, "properties is null");
    }

    @Override
    public void notifyObservers(final UserObserverDTO dto) {
      properties.kafkaTopics().keySet()
          .forEach(topic -> kafka.sendEvent(topic, dto.getUsername(), dto));
    }

    @Override
    public void setupObserver() {
      properties.kafkaTopics()
          .forEach((topic, config) -> kafka.createTopicIfNotExists(topic,
              config.partitions(), config.replicationFactor()));
    }
  }
}
