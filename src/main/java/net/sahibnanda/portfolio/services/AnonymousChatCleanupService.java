package net.sahibnanda.portfolio.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.repository.AnonymousChatRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically purges anonymous chats that have gone idle, so ephemeral visitor
 * conversations don't accumulate indefinitely.
 */
@Slf4j
@Service
public final class AnonymousChatCleanupService {

  /** A chat idle longer than this is eligible for purging. */
  private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(10);

  /** Repository used to delete idle anonymous chats. */
  private final AnonymousChatRepository anonymousChats;

  /**
   * Creates a new anonymous chat cleanup service.
   *
   * @param anonymousChatRepository repository used to delete idle anonymous
   *        chats
   */
  public AnonymousChatCleanupService(
      final AnonymousChatRepository anonymousChatRepository) {
    this.anonymousChats = Objects.requireNonNull(anonymousChatRepository,
        "anonymousChatRepository is null");
  }

  /**
   * Deletes every anonymous chat last updated more than {@link #IDLE_THRESHOLD}
   * ago.
   */
  @Scheduled(cron = "0 */30 * * * *")
  public void purgeIdleChats() {
    LocalDateTime cutoff = LocalDateTime.now().minus(IDLE_THRESHOLD);
    int deleted = anonymousChats.deleteIdleOlderThan(cutoff);
    log.info("Purged {} idle anonymous chat(s) older than {}.", deleted,
        cutoff);
  }
}
