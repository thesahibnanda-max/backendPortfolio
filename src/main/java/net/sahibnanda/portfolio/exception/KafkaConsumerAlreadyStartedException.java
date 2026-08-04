package net.sahibnanda.portfolio.exception;

/**
 * Thrown when {@link net.sahibnanda.portfolio.queue.Kafka#startConsumer} is
 * called for a topic that already has an active consumer running.
 */
public final class KafkaConsumerAlreadyStartedException
    extends RuntimeException {

  /**
   * Constructs a new exception with the given detail message.
   *
   * @param message the detail message describing the failure
   */
  public KafkaConsumerAlreadyStartedException(final String message) {
    super(message);
  }
}
