package net.sahibnanda.portfolio.services;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.enums.ContextType;
import net.sahibnanda.portfolio.utils.ContextFormatter;
import org.springframework.stereotype.Service;

/**
 * Loads and formats only the knowledge domains the Orchestrator AI decided are
 * required, concatenated in {@link ContextType}'s declared order (matching the
 * PRD's fixed Profile-GitHub-LeetCode-Codeforces-Personality sequence)
 * regardless of the order they were requested in.
 */
@Slf4j
@Service
public final class ContextAggregatorService {

  /** Service used to fetch each domain's details. */
  private final DetailsService detailsService;

  /** Executor used to fetch multiple requested domains concurrently. */
  private final ExecutorService executor =
      Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Creates a new context aggregator service.
   *
   * @param detailsServiceClient service used to fetch each domain's details
   */
  public ContextAggregatorService(final DetailsService detailsServiceClient) {
    this.detailsService = Objects.requireNonNull(detailsServiceClient,
        "detailsServiceClient must not be null");
  }

  /**
   * Loads and formats every requested knowledge domain.
   *
   * @param requiredContexts the domains the Orchestrator AI selected
   * @return the concatenated, compact-text aggregated context, or an empty
   *         string if {@code requiredContexts} is null, empty, or contains
   *         {@link ContextType#NONE}
   * @throws RuntimeException if fetching any requested domain fails
   */
  public String aggregate(final List<ContextType> requiredContexts) {
    if (requiredContexts == null || requiredContexts.isEmpty()
        || requiredContexts.contains(ContextType.NONE)) {
      return "";
    }

    Map<ContextType, Future<String>> futures = new EnumMap<>(ContextType.class);
    for (ContextType contextType : requiredContexts) {
      futures.putIfAbsent(contextType,
          executor.submit(() -> fetchAndFormat(contextType)));
    }

    StringBuilder aggregated = new StringBuilder();
    for (Future<String> future : futures.values()) {
      String formatted = await(future);
      if (!formatted.isBlank()) {
        if (!aggregated.isEmpty()) {
          aggregated.append('\n');
        }
        aggregated.append(formatted);
      }
    }
    return aggregated.toString();
  }

  private String fetchAndFormat(final ContextType contextType) {
    return switch (contextType) {
      case PROFILE ->
        ContextFormatter.formatProfile(detailsService.getProfileDetails());
      case GITHUB ->
        ContextFormatter.formatGithub(detailsService.getGithubDetails());
      case LEETCODE ->
        ContextFormatter.formatLeetcode(detailsService.getLeetcodeDetails());
      case CODEFORCES -> ContextFormatter
          .formatCodeforces(detailsService.getCodeforcesDetails());
      case PERSONALITY -> ContextFormatter
          .formatPersonality(detailsService.getPersonalityDetails());
      case NONE -> "";
    };
  }

  private static String await(final Future<String> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while aggregating context.",
          e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Failed to aggregate context.",
          e.getCause());
    }
  }
}
