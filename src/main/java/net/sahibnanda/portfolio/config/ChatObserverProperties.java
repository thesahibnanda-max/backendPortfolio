package net.sahibnanda.portfolio.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the chat repository observer.
 *
 * @param kafkaTopics every Kafka topic chat events are published to, keyed by
 *        topic name
 */
@ConfigurationProperties(prefix = "chat-observer")
public record ChatObserverProperties(Map<String, KafkaTopics> kafkaTopics) {

  /**
   * A Kafka topic's provisioning settings.
   *
   * @param partitions number of partitions
   * @param replicationFactor replication factor
   */
  public record KafkaTopics(Integer partitions, Short replicationFactor) {
  }
}
