package net.sahibnanda.portfolio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.sahibnanda.portfolio.config.LeetcodeProperties;
import net.sahibnanda.portfolio.models.LeetcodeUserProfileRequest;
import net.sahibnanda.portfolio.models.LeetcodeUserProfileResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LeetcodeClientTest {

  private static LeetcodeClient leetcodeClient;

  @BeforeAll
  static void setup() {
    leetcodeClient =
        new LeetcodeClient(new LeetcodeProperties("https://leetcode.com"));
  }

  @Test
  void getUserProfileDetails() {
    LeetcodeUserProfileResponse resp =
        leetcodeClient.getUserProfileDetails(LeetcodeUserProfileRequest
            .builder("imsahibnanda").everything().build());
    System.out.println(resp);

    assertNotNull(resp);
    assertNull(resp.getErrors());
    assertNotNull(resp.getData());

    LeetcodeUserProfileResponse.MatchedUser matchedUser =
        resp.getData().getMatchedUser();
    assertNotNull(matchedUser);
    assertEquals("imsahibnanda", matchedUser.getUsername());

    assertNotNull(matchedUser.getProfile());
    assertNotNull(matchedUser.getSubmitStatsGlobal());
    assertNotNull(matchedUser.getSubmitStatsGlobal().getAcSubmissionNum());
    assertFalse(
        matchedUser.getSubmitStatsGlobal().getAcSubmissionNum().isEmpty());

    assertNotNull(matchedUser.getUserCalendar());
    assertNotNull(matchedUser.getUserCalendar().getSubmissionCalendar());
    assertFalse(
        matchedUser.getUserCalendar().getSubmissionCalendar().isEmpty());

    assertNotNull(resp.getData().getUserContestRanking());
    assertNotNull(resp.getData().getUserContestRankingHistory());
    assertFalse(resp.getData().getUserContestRankingHistory().isEmpty());
  }
}
