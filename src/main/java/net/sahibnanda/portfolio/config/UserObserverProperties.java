package net.sahibnanda.portfolio.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the user repository observer.
 *
 * @param kafkaTopics every Kafka topic user events are published to, keyed by
 *        topic name
 */
@ConfigurationProperties(prefix = "user-observer")
public record UserObserverProperties(Map<String, KafkaTopics> kafkaTopics) {

  /**
   * A Kafka topic's provisioning settings.
   *
   * @param partitions number of partitions
   * @param replicationFactor replication factor
   */
  public record KafkaTopics(Integer partitions, Short replicationFactor) {
  }
}
