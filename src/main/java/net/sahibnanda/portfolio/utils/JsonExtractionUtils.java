package net.sahibnanda.portfolio.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.sahibnanda.portfolio.exception.JsonExtractionException;

/**
 * Extracts a JSON object embedded somewhere in a raw LLM response, tolerating
 * the common ways weaker models fail to follow "JSON only" instructions:
 * markdown code fences around the JSON, prose before/after it, and small
 * malformations like a trailing comma.
 */
@UtilityClass
public final class JsonExtractionUtils {

  /** Matches a trailing comma right before a closing {@code }}/{@code ]}. */
  private static final Pattern TRAILING_COMMA =
      Pattern.compile(",(\\s*[}\\]])");

  /** Candidate finders, tried in order from most to least confident. */
  private static final List<JsonCandidateFinder> FINDERS =
      List.of(new DirectJsonFinder(), new FencedCodeBlockFinder(),
          new BraceMatchFinder());

  /**
   * Extracts and parses a JSON object embedded somewhere in {@code raw}.
   *
   * @param raw the raw LLM response text
   * @param type the target type
   * @param <T> the target type
   * @return the parsed object
   * @throws JsonExtractionException if no valid JSON object could be extracted
   *         by any strategy
   */
  public <T> T extractJson(final String raw, final Class<T> type) {
    String trimmed = raw == null ? "" : raw.trim();
    if (trimmed.isEmpty()) {
      throw new JsonExtractionException(
          "Cannot extract JSON from an empty response.");
    }

    for (JsonCandidateFinder finder : FINDERS) {
      for (String candidate : finder.find(trimmed)) {
        Optional<T> parsed = parseCandidate(candidate, type);
        if (parsed.isPresent()) {
          return parsed.get();
        }
      }
    }

    throw new JsonExtractionException(
        "Could not extract valid JSON from response: " + trimmed);
  }

  private static <T> Optional<T> parseCandidate(final String candidate,
      final Class<T> type) {
    String trimmed = candidate.trim();
    if (trimmed.isEmpty()) {
      return Optional.empty();
    }

    Optional<T> direct = JsonUtils.tryFromJson(trimmed, type);
    if (direct.isPresent()) {
      return direct;
    }

    String sanitized = TRAILING_COMMA.matcher(trimmed).replaceAll("$1");
    return sanitized.equals(trimmed) ? Optional.empty()
        : JsonUtils.tryFromJson(sanitized, type);
  }

  /** Finds one or more JSON-object candidate substrings in a raw response. */
  private interface JsonCandidateFinder {
    List<String> find(String raw);
  }

  /**
   * Tries the raw response as-is -- the cheapest and most common case when the
   * model actually followed instructions.
   */
  private static final class DirectJsonFinder implements JsonCandidateFinder {
    @Override
    public List<String> find(final String raw) {
      return List.of(raw);
    }
  }

  /** Matches a {@code ```json ... ```} fenced code block. */
  private static final Pattern TRIPLE_BACKTICK_JSON_FENCE =
      Pattern.compile("(?is)```json\\s*\\n?(.*?)\\n?\\s*```");

  /** Matches a plain {@code ``` ... ```} fenced code block. */
  private static final Pattern TRIPLE_BACKTICK_FENCE =
      Pattern.compile("(?s)```\\s*\\n?(.*?)\\n?\\s*```");

  /** Matches a single-backtick {@code `...`} fenced span. */
  private static final Pattern SINGLE_BACKTICK_FENCE =
      Pattern.compile("(?s)`([^`]*?)`");

  /**
   * Looks for JSON wrapped in a markdown code fence: tries, in order, every
   * {@code ```json} fence, then every plain {@code ```} fence, then every
   * single-backtick fence -- stopping at the first tier that finds any blocks
   * at all, so a real triple-backtick block is never diluted by a spurious
   * single-backtick match inside it.
   */
  private static final class FencedCodeBlockFinder
      implements JsonCandidateFinder {
    @Override
    public List<String> find(final String raw) {
      List<String> jsonFenced = findAllFenced(TRIPLE_BACKTICK_JSON_FENCE, raw);
      if (!jsonFenced.isEmpty()) {
        return jsonFenced;
      }
      List<String> plainFenced = findAllFenced(TRIPLE_BACKTICK_FENCE, raw);
      if (!plainFenced.isEmpty()) {
        return plainFenced;
      }
      return findAllFenced(SINGLE_BACKTICK_FENCE, raw);
    }

    private static List<String> findAllFenced(final Pattern pattern,
        final String raw) {
      Matcher matcher = pattern.matcher(raw);
      List<String> candidates = new ArrayList<>();
      while (matcher.find()) {
        String candidate = matcher.group(1).trim();
        if (!candidate.isEmpty()) {
          candidates.add(candidate);
        }
      }
      return candidates;
    }
  }

  /**
   * Last-resort strategy: scans the entire string for every top-level
   * {@code {...}} object, tracking string-literal/escape state so braces inside
   * quoted strings are ignored, and keeps scanning after each closed object
   * instead of stopping at the first one -- this recovers cases where a model
   * prefixes the real JSON with prose that itself contains a decoy '{' (the
   * decoy is tried and rejected, and the scan continues to the real object
   * further down).
   */
  private static final class BraceMatchFinder implements JsonCandidateFinder {
    @Override
    public List<String> find(final String raw) {
      List<String> candidates = new ArrayList<>();
      BraceScanner scanner = new BraceScanner();
      for (int i = 0; i < raw.length(); i++) {
        scanner.step(raw, i).ifPresent(candidates::add);
      }
      return candidates;
    }
  }

  /**
   * Tracks brace depth and string-literal/escape state one character at a time,
   * so {@link BraceMatchFinder} can find every top-level {@code {...}} object
   * in a string while correctly ignoring braces inside quoted string values
   * (respecting {@code \"} escapes).
   */
  private static final class BraceScanner {
    /** Current nesting depth of top-level {@code {}} brace pairs. */
    private int depth;

    /** Index where the current top-level object started, or -1 if none. */
    private int start = -1;

    /** Whether the scanner is currently inside a quoted string literal. */
    private boolean inString;

    /**
     * Whether the previous character inside a string was an unconsumed
     * {@code \}.
     */
    private boolean escaped;

    Optional<String> step(final String raw, final int i) {
      char c = raw.charAt(i);

      if (inString) {
        stepInString(c);
        return Optional.empty();
      }

      switch (c) {
        case '"' -> {
          inString = true;
        }
        case '{' -> {
          if (depth == 0) {
            start = i;
          }
          depth++;
        }
        case '}' -> {
          return closeBrace(raw, i);
        }
        default -> {
        }
      }

      return Optional.empty();
    }

    private void stepInString(final char c) {
      if (escaped) {
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else if (c == '"') {
        inString = false;
      }
    }

    private Optional<String> closeBrace(final String raw, final int i) {
      if (depth == 0) {
        return Optional.empty();
      }

      depth--;
      if (depth != 0 || start < 0) {
        return Optional.empty();
      }

      String candidate = raw.substring(start, i + 1);
      start = -1;
      return Optional.of(candidate);
    }
  }
}
