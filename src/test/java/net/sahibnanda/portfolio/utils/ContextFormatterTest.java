package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.models.PersonalityResponse;
import net.sahibnanda.portfolio.models.ProfileResponse;
import net.sahibnanda.portfolio.objects.CodeforcesDetails;
import net.sahibnanda.portfolio.objects.GitHubDetails;
import net.sahibnanda.portfolio.objects.LeetcodeDetails;
import net.sahibnanda.portfolio.objects.PersonalityDetails;
import net.sahibnanda.portfolio.objects.ProfileDetails;
import org.junit.jupiter.api.Test;

class ContextFormatterTest {

  @Test
  void formatProfileIncludesNameSkillsAndExperience() {
    ProfileDetails profile = ProfileDetails.builder()
        .profileDetails(ProfileResponse.ProfileDetails.builder()
            .name("Sahib Nanda").email("sabbykabby12@gmail.com").build())
        .countryName("India").linkedinUrl("https://linkedin.com/in/sahib-nanda")
        .skillsByCategory(Map.of("Languages", List.of("Go", "Java")))
        .experience(List.of(ProfileResponse.Experience.builder().title("SDE")
            .company("CRED").startDate("2025-06").endDate("2026-06")
            .description(List.of("Built things."))
            .technologies(List.of("Go", "Java")).build()))
        .build();

    String formatted = ContextFormatter.formatProfile(profile);

    assertThat(formatted).contains("PROFILE:").contains("Sahib Nanda")
        .contains("India").contains("Languages: Go, Java")
        .contains("SDE at CRED").contains("[Go, Java]");
  }

  @Test
  void formatGithubKeepsOnlyTopFiveReposByStars() {
    GitHubDetails.RepositorySummary[] repos =
        new GitHubDetails.RepositorySummary[8];
    for (int i = 0; i < 8; i++) {
      repos[i] = GitHubDetails.RepositorySummary.builder().name("repo" + i)
          .stars(i).description("desc" + i).build();
    }

    GitHubDetails details =
        GitHubDetails.builder().username("thesahibnanda").publicRepos(8)
            .followers(10).following(5).repositories(List.of(repos)).build();

    String formatted = ContextFormatter.formatGithub(List.of(details));

    assertThat(formatted).contains("GITHUB (thesahibnanda)").contains("repo7")
        .contains("repo6").contains("repo5").contains("repo4")
        .contains("repo3");
    assertThat(formatted).doesNotContain("repo2").doesNotContain("repo1")
        .doesNotContain("repo0");
  }

  @Test
  void formatGithubReturnsEmptyForEmptyList() {
    assertThat(ContextFormatter.formatGithub(List.of())).isEmpty();
    assertThat(ContextFormatter.formatGithub(null)).isEmpty();
  }

  @Test
  void formatLeetcodeIncludesRankSolvedCountsAndBadges() {
    LeetcodeDetails details = LeetcodeDetails.builder().username("imsahibnanda")
        .ranking(115794).totalSolved(643).easySolved(213).mediumSolved(339)
        .hardSolved(91).badges(List.of("50 Days Badge 2025"))
        .contestRating(1537.445).build();

    String formatted = ContextFormatter.formatLeetcode(List.of(details));

    assertThat(formatted).contains("LEETCODE (imsahibnanda)")
        .contains("rank 115794").contains("643 solved")
        .contains("Badges: 50 Days Badge 2025");
  }

  @Test
  void formatCodeforcesKeepsOnlyFiveMostRecentTransitions() {
    List<CodeforcesDetails.RatingTransition> transitions =
        new java.util.ArrayList<>();
    for (int i = 0; i < 7; i++) {
      transitions.add(CodeforcesDetails.RatingTransition.builder()
          .contestName("Contest " + i).rank(i).oldRating(1000 + i * 10)
          .newRating(1000 + (i + 1) * 10)
          .contestTime(Instant.ofEpochSecond(1000L * i)).build());
    }

    CodeforcesDetails details = CodeforcesDetails.builder()
        .handle("shisukenohara").currentRating(1832).maxRating(1987)
        .contestsCount(7).ratingHistory(transitions).build();

    String formatted = ContextFormatter.formatCodeforces(List.of(details));

    assertThat(formatted).contains("CODEFORCES (shisukenohara)")
        .contains("Contest 6").contains("Contest 5").contains("Contest 4")
        .contains("Contest 3").contains("Contest 2");
    assertThat(formatted).doesNotContain("Contest 1")
        .doesNotContain("Contest 0");
  }

  @Test
  void formatPersonalityIncludesAboutMeAndTraits() {
    PersonalityDetails personality = PersonalityDetails.builder()
        .aboutMe("I build distributed systems.")
        .personalProfile(PersonalityResponse.PersonalProfile.builder()
            .personality(PersonalityResponse.Personality.builder()
                .coreTraits(List.of("Curious", "Persistent")).build())
            .interests(PersonalityResponse.Interests.builder()
                .technology(List.of("Distributed systems")).build())
            .build())
        .build();

    String formatted = ContextFormatter.formatPersonality(personality);

    assertThat(formatted).contains("PERSONALITY:")
        .contains("I build distributed systems.")
        .contains("Core traits: Curious, Persistent")
        .contains("Technology interests: Distributed systems");
  }

  @Test
  void formatPersonalityReturnsEmptyForNull() {
    assertThat(ContextFormatter.formatPersonality(null)).isEmpty();
  }
}
