package net.sahibnanda.portfolio.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.sahibnanda.portfolio.models.PersonalityResponse;
import net.sahibnanda.portfolio.models.ProfileResponse;
import net.sahibnanda.portfolio.objects.CodeforcesDetails;
import net.sahibnanda.portfolio.objects.GitHubDetails;
import net.sahibnanda.portfolio.objects.LeetcodeDetails;
import net.sahibnanda.portfolio.objects.PersonalityDetails;
import net.sahibnanda.portfolio.objects.ProfileDetails;

/**
 * Formats each knowledge domain's details into a compact, token-efficient
 * plain-text block for the Worker AI's prompt -- deliberately not JSON, since
 * JSON's braces/quotes/repeated keys cost meaningfully more tokens than terse
 * "Label: value" lines for the same information.
 */
@UtilityClass
public final class ContextFormatter {

  /** Maximum GitHub repositories shown per user, ranked by star count. */
  private static final int MAX_GITHUB_REPOS_SHOWN = 5;

  /** Maximum Codeforces rating transitions shown, most recent first. */
  private static final int MAX_RATING_TRANSITIONS_SHOWN = 5;

  /**
   * Formats the portfolio owner's profile.
   *
   * @param profile the profile details
   * @return the compact text block
   */
  public String formatProfile(final ProfileDetails profile) {
    StringBuilder sb = new StringBuilder("PROFILE:\n");
    ProfileResponse.ProfileDetails identity = profile.getProfileDetails();
    if (identity != null) {
      appendIfNotBlank(sb, "Name", identity.getName());
      appendIfNotBlank(sb, "Email", identity.getEmail());
    }
    appendIfNotBlank(sb, "Country", profile.getCountryName());
    appendIfNotBlank(sb, "LinkedIn", profile.getLinkedinUrl());
    appendIfNotBlank(sb, "Twitter", profile.getTwitterUrl());
    appendListIfNotEmpty(sb, "Websites", profile.getWebsites());
    appendListIfNotEmpty(sb, "Languages spoken", profile.getLanguages());
    appendListIfNotEmpty(sb, "GitHub usernames", profile.getGithubUsernames());
    appendListIfNotEmpty(sb, "LeetCode usernames",
        profile.getLeetcodeUsernames());
    appendListIfNotEmpty(sb, "Codeforces usernames",
        profile.getCodeforcesUsernames());

    if (profile.getSkillsByCategory() != null
        && !profile.getSkillsByCategory().isEmpty()) {
      sb.append("Skills:\n");
      for (Map.Entry<String, List<String>> entry : profile.getSkillsByCategory()
          .entrySet()) {
        sb.append("- ").append(entry.getKey()).append(": ")
            .append(String.join(", ", entry.getValue())).append('\n');
      }
    }
    appendListIfNotEmpty(sb, "Achievements", profile.getAchievements());

    if (profile.getExperience() != null && !profile.getExperience().isEmpty()) {
      sb.append("Experience:\n");
      for (ProfileResponse.Experience exp : profile.getExperience()) {
        sb.append("- ").append(exp.getTitle()).append(" at ")
            .append(exp.getCompany()).append(" (").append(exp.getStartDate())
            .append(" to ").append(exp.getEndDate()).append("): ")
            .append(String.join(" ", exp.getDescription())).append(" [")
            .append(String.join(", ", exp.getTechnologies())).append("]\n");
      }
    }

    if (profile.getEducation() != null && !profile.getEducation().isEmpty()) {
      sb.append("Education:\n");
      for (ProfileResponse.Education edu : profile.getEducation()) {
        sb.append("- ").append(edu.getDegree()).append(", ")
            .append(edu.getField()).append(", ").append(edu.getInstitution())
            .append(" (").append(edu.getStartDate()).append(" to ")
            .append(edu.getEndDate()).append("), Grade: ")
            .append(edu.getGrade()).append('\n');
      }
    }

    if (profile.getProjects() != null && !profile.getProjects().isEmpty()) {
      sb.append("Projects:\n");
      for (ProfileResponse.Project project : profile.getProjects()) {
        sb.append("- ").append(project.getName()).append(" (")
            .append(project.getYear()).append("): ")
            .append(String.join(" ", project.getDescription())).append(" [")
            .append(String.join(", ", project.getTechnologies())).append("]\n");
      }
    }

    return sb.toString();
  }

  /**
   * Formats every configured GitHub user's profile and top repositories.
   *
   * @param githubDetailsList one entry per configured GitHub username
   * @return the compact text block, or empty if the list is empty
   */
  public String formatGithub(final List<GitHubDetails> githubDetailsList) {
    if (githubDetailsList == null || githubDetailsList.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (GitHubDetails details : githubDetailsList) {
      sb.append("GITHUB (").append(details.getUsername()).append("): ")
          .append(details.getPublicRepos()).append(" public repos, ")
          .append(details.getFollowers()).append(" followers, ")
          .append(details.getFollowing()).append(" following.");
      if (details.getBio() != null && !details.getBio().isBlank()) {
        sb.append(" Bio: ").append(details.getBio());
      }
      sb.append('\n');

      List<GitHubDetails.RepositorySummary> repositories =
          details.getRepositories();
      List<GitHubDetails.RepositorySummary> topRepos =
          repositories == null ? List.of()
              : repositories.stream()
                  .sorted(Comparator.comparing(
                      GitHubDetails.RepositorySummary::getStars,
                      Comparator.nullsLast(Comparator.reverseOrder())))
                  .limit(MAX_GITHUB_REPOS_SHOWN).toList();
      if (!topRepos.isEmpty()) {
        sb.append("Top repos: ")
            .append(topRepos.stream().map(ContextFormatter::formatRepo)
                .collect(Collectors.joining("; ")))
            .append('\n');
      }
    }
    return sb.toString();
  }

  private static String formatRepo(final GitHubDetails.RepositorySummary repo) {
    StringBuilder sb = new StringBuilder(repo.getName());
    if (repo.getLanguage() != null) {
      sb.append(" (").append(repo.getLanguage()).append(')');
    }
    sb.append(" - ").append(repo.getStars()).append(" stars");
    if (repo.getDescription() != null && !repo.getDescription().isBlank()) {
      sb.append(": ").append(repo.getDescription());
    }
    return sb.toString();
  }

  /**
   * Formats every configured LeetCode user's stats.
   *
   * @param leetcodeDetailsList one entry per configured LeetCode username
   * @return the compact text block, or empty if the list is empty
   */
  public String formatLeetcode(
      final List<LeetcodeDetails> leetcodeDetailsList) {
    if (leetcodeDetailsList == null || leetcodeDetailsList.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (LeetcodeDetails details : leetcodeDetailsList) {
      sb.append("LEETCODE (").append(details.getUsername()).append("): rank ")
          .append(details.getRanking()).append(", ")
          .append(details.getTotalSolved()).append(" solved (")
          .append(details.getEasySolved()).append(" easy, ")
          .append(details.getMediumSolved()).append(" medium, ")
          .append(details.getHardSolved()).append(" hard)");
      if (details.getContestRating() != null) {
        sb.append(", contest rating ").append(details.getContestRating());
      }
      if (details.getCurrentStreak() != null) {
        sb.append(", current streak ").append(details.getCurrentStreak())
            .append(" days");
      }
      sb.append('\n');
      appendListIfNotEmpty(sb, "Badges", details.getBadges());
      appendMapIfNotEmpty(sb, "Languages used",
          details.getLanguageProblemsSolved());
    }
    return sb.toString();
  }

  /**
   * Formats every configured Codeforces handle's stats.
   *
   * @param codeforcesDetailsList one entry per configured Codeforces handle
   * @return the compact text block, or empty if the list is empty
   */
  public String formatCodeforces(
      final List<CodeforcesDetails> codeforcesDetailsList) {
    if (codeforcesDetailsList == null || codeforcesDetailsList.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (CodeforcesDetails details : codeforcesDetailsList) {
      sb.append("CODEFORCES (").append(details.getHandle())
          .append("): current rating ").append(details.getCurrentRating())
          .append(", max rating ").append(details.getMaxRating()).append(", ")
          .append(details.getContestsCount()).append(" rated contests\n");

      List<CodeforcesDetails.RatingTransition> history =
          details.getRatingHistory();
      List<CodeforcesDetails.RatingTransition> recent = history == null
          ? List.of()
          : history.stream()
              .skip(Math.max(0, history.size() - MAX_RATING_TRANSITIONS_SHOWN))
              .toList();
      if (!recent.isEmpty()) {
        sb.append("Recent contests: ")
            .append(recent.stream()
                .map(t -> t.getContestName() + " (rank " + t.getRank() + "): "
                    + t.getOldRating() + "->" + t.getNewRating())
                .collect(Collectors.joining("; ")))
            .append('\n');
      }
    }
    return sb.toString();
  }

  /**
   * Formats the portfolio owner's personality profile.
   *
   * @param personality the personality details
   * @return the compact text block, or empty if {@code personality} is null
   */
  public String formatPersonality(final PersonalityDetails personality) {
    if (personality == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder("PERSONALITY:\n");
    appendIfNotBlank(sb, "About me", personality.getAboutMe());

    PersonalityResponse.PersonalProfile profile =
        personality.getPersonalProfile();
    if (profile == null) {
      return sb.toString();
    }

    appendBasicInfo(sb, profile.getBasicInfo());
    appendAppearance(sb, profile.getPhysicalAppearance());
    appendPersonalityTraits(sb, profile.getPersonality());
    appendInterests(sb, profile.getInterests());
    appendFavorites(sb, profile.getFavorites());
    appendLifestyle(sb, profile.getLifestyle());

    if (profile.getLanguages() != null && !profile.getLanguages().isEmpty()) {
      sb.append("Languages: ")
          .append(profile.getLanguages().stream()
              .map(l -> l.getName() + " (" + l.getProficiency() + ")")
              .collect(Collectors.joining(", ")))
          .append('\n');
    }

    return sb.toString();
  }

  private static void appendBasicInfo(final StringBuilder sb,
      final PersonalityResponse.BasicInfo basicInfo) {
    if (basicInfo == null) {
      return;
    }
    sb.append("Nationality: ").append(basicInfo.getNationality())
        .append(", Gender: ").append(basicInfo.getGender());
    if (basicInfo.getHeight() != null) {
      sb.append(", Height: ").append(basicInfo.getHeight().getFeet())
          .append("ft (").append(basicInfo.getHeight().getCentimeters())
          .append("cm)");
    }
    sb.append('\n');
  }

  private static void appendAppearance(final StringBuilder sb,
      final PersonalityResponse.PhysicalAppearance appearance) {
    if (appearance == null) {
      return;
    }
    sb.append("Appearance: ").append(appearance.getBodyType())
        .append(" build, ").append(appearance.getPhysique());
    if (appearance.getHair() != null) {
      sb.append(", ").append(appearance.getHair().getColor()).append(' ')
          .append(appearance.getHair().getTexture()).append(" hair");
    }
    if (appearance.getEyes() != null) {
      sb.append(", ").append(appearance.getEyes().getColor()).append(" eyes");
    }
    if (appearance.getFashion() != null) {
      appendListIfNotEmpty(sb, "Favorite colors",
          appearance.getFashion().getFavoriteColors());
    }
    sb.append('\n');
  }

  private static void appendPersonalityTraits(final StringBuilder sb,
      final PersonalityResponse.Personality traits) {
    if (traits == null) {
      return;
    }
    appendListIfNotEmpty(sb, "Core traits", traits.getCoreTraits());
    appendListIfNotEmpty(sb, "Professional traits",
        traits.getProfessionalTraits());
    appendListIfNotEmpty(sb, "Personal values", traits.getPersonalValues());
    if (traits.getWorkPreferences() != null) {
      appendListIfNotEmpty(sb, "Preferred engineering domains",
          traits.getWorkPreferences().getPreferredDomains());
      appendListIfNotEmpty(sb, "Engineering values",
          traits.getWorkPreferences().getEngineeringValues());
    }
  }

  private static void appendInterests(final StringBuilder sb,
      final PersonalityResponse.Interests interests) {
    if (interests == null) {
      return;
    }
    if (interests.getSports() != null && !interests.getSports().isEmpty()) {
      sb.append("Sports: ")
          .append(interests.getSports().entrySet().stream()
              .map(e -> e.getKey() + " (team: " + e.getValue().getFavoriteTeam()
                  + ", player: " + e.getValue().getFavoritePlayer() + ")")
              .collect(Collectors.joining("; ")))
          .append('\n');
    }
    appendListIfNotEmpty(sb, "Fitness interests", interests.getFitness());
    appendListIfNotEmpty(sb, "Technology interests", interests.getTechnology());
  }

  private static void appendFavorites(final StringBuilder sb,
      final PersonalityResponse.Favorites favorites) {
    if (favorites == null) {
      return;
    }
    appendTitledWorks(sb, "Favorite movies", favorites.getMovies());
    appendTitledWorks(sb, "Favorite games", favorites.getGames());
    if (favorites.getArtists() != null && !favorites.getArtists().isEmpty()) {
      sb.append("Favorite artists: ")
          .append(favorites.getArtists().stream()
              .map(a -> a.getName() + " (" + a.getType() + ")")
              .collect(Collectors.joining(", ")))
          .append('\n');
    }
  }

  private static void appendTitledWorks(final StringBuilder sb,
      final String label, final List<PersonalityResponse.TitledWork> works) {
    if (works == null || works.isEmpty()) {
      return;
    }
    sb.append(label).append(": ")
        .append(
            works.stream().map(w -> w.getTitle() + " (" + w.getGenre() + ")")
                .collect(Collectors.joining(", ")))
        .append('\n');
  }

  private static void appendLifestyle(final StringBuilder sb,
      final PersonalityResponse.Lifestyle lifestyle) {
    if (lifestyle == null) {
      return;
    }
    List<String> flags = new ArrayList<>();
    if (Boolean.TRUE.equals(lifestyle.getFitnessFocused())) {
      flags.add("fitness-focused");
    }
    if (Boolean.TRUE.equals(lifestyle.getSportsEnthusiast())) {
      flags.add("sports enthusiast");
    }
    if (Boolean.TRUE.equals(lifestyle.getTechnologyEnthusiast())) {
      flags.add("technology enthusiast");
    }
    if (Boolean.TRUE.equals(lifestyle.getContinuousLearning())) {
      flags.add("continuous learner");
    }
    if (!flags.isEmpty()) {
      sb.append("Lifestyle: ").append(String.join(", ", flags)).append('\n');
    }
  }

  private static void appendIfNotBlank(final StringBuilder sb,
      final String label, final String value) {
    if (value != null && !value.isBlank()) {
      sb.append(label).append(": ").append(value).append('\n');
    }
  }

  private static void appendListIfNotEmpty(final StringBuilder sb,
      final String label, final List<String> values) {
    if (values != null && !values.isEmpty()) {
      sb.append(label).append(": ").append(String.join(", ", values))
          .append('\n');
    }
  }

  private static void appendMapIfNotEmpty(final StringBuilder sb,
      final String label, final Map<String, Integer> values) {
    if (values != null && !values.isEmpty()) {
      sb.append(label).append(": ")
          .append(values.entrySet().stream()
              .map(e -> e.getKey() + " " + e.getValue())
              .collect(Collectors.joining(", ")))
          .append('\n');
    }
  }
}
