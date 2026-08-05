package net.sahibnanda.portfolio.objects;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * The portfolio owner's publicly shareable professional links: a public profile
 * link for every tracked LeetCode/Codeforces/GitHub account, the resume, the
 * profile photo(s), and the personal websites/Twitter recorded on the primary
 * LeetCode account.
 */
@Builder
@Data
public final class ProfessionalDetails {

  /** Public LeetCode profile URL for every configured LeetCode username. */
  private final List<String> leetcodeLinks;

  /** Public Codeforces profile URL for every configured Codeforces handle. */
  private final List<String> codeforcesLink;

  /** Public GitHub profile URL for every configured GitHub username. */
  private final List<String> githubLinks;

  /** URL of the portfolio owner's resume. */
  private final String resumeLink;

  /** URLs of the portfolio owner's profile photo(s). */
  private final List<String> profilePhotoLink;

  /** Personal websites, as recorded on the primary LeetCode account. */
  private final List<String> websites;

  /** Twitter profile URL, as recorded on the primary LeetCode account. */
  private final String twitterUrl;
}
