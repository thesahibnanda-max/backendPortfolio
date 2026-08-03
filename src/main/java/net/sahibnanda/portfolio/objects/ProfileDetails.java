package net.sahibnanda.portfolio.objects;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import net.sahibnanda.portfolio.models.ProfileResponse;

/**
 * The portfolio owner's profile, composed from the raw {@link ProfileResponse}
 * plus the tracked platform handles (sourced from {@code DetailsProperties},
 * the same list used to actually fetch
 * {@link LeetcodeDetails}/{@link CodeforcesDetails}/{@link GitHubDetails}, so
 * these can never drift out of sync) and identity fields LeetCode happens to
 * record on its own profile that {@link ProfileResponse} doesn't already cover
 * elsewhere (LinkedIn, Twitter, personal websites, country). LeetCode's own
 * {@code company}/{@code jobTitle}/ {@code school} are deliberately left out
 * here since {@link ProfileResponse#getExperience()} and
 * {@link ProfileResponse#getEducation()} are already the authoritative source
 * for that; a GitHub profile link is likewise left out since
 * {@link #getGithubUsernames()} already covers it — duplicating either would
 * risk the two going out of sync.
 */
@Builder
@Data
public final class ProfileDetails {

  /** Basic identity details for the portfolio owner. */
  private final ProfileResponse.ProfileDetails profileDetails;

  /** Notable projects the portfolio owner has worked on. */
  private final List<ProfileResponse.Project> projects;

  /** Languages the portfolio owner speaks. */
  private final List<String> languages;

  /** Notable achievements and certifications. */
  private final List<String> achievements;

  /** Professional work experience, most relevant first. */
  private final List<ProfileResponse.Experience> experience;

  /** Educational background. */
  private final List<ProfileResponse.Education> education;

  /** Skills grouped by category name. */
  private final Map<String, List<String>> skillsByCategory;

  /** LeetCode usernames tracked for this portfolio. */
  private final List<String> leetcodeUsernames;

  /** Codeforces handles tracked for this portfolio. */
  private final List<String> codeforcesUsernames;

  /** GitHub usernames tracked for this portfolio. */
  private final List<String> githubUsernames;

  /** LinkedIn profile URL, as recorded on the primary LeetCode account. */
  private final String linkedinUrl;

  /** Twitter profile URL, as recorded on the primary LeetCode account. */
  private final String twitterUrl;

  /** Personal websites, as recorded on the primary LeetCode account. */
  private final List<String> websites;

  /** Country, as recorded on the primary LeetCode account. */
  private final String countryName;
}
