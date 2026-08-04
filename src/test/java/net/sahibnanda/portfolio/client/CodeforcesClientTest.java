package net.sahibnanda.portfolio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.sahibnanda.portfolio.config.CodeforcesProperties;
import net.sahibnanda.portfolio.models.CodeforcesUserRatingResponse;
import net.sahibnanda.portfolio.utils.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CodeforcesClientTest {

  private static CodeforcesClient codeforcesClient;

  @BeforeAll
  static void setup() {
    codeforcesClient = new CodeforcesClient(
        new CodeforcesProperties(TestEnvironment.CODEFORCES_BASE_URL));
  }

  @Test
  void getUserRatingDetails() {
    CodeforcesUserRatingResponse resp =
        codeforcesClient.getUserRatingDetails("shisukenohara");
    System.out.println(resp);

    assertNotNull(resp);
    assertEquals("OK", resp.getStatus());
    assertNotNull(resp.getResult());
    assertFalse(resp.getResult().isEmpty());

    CodeforcesUserRatingResponse.RatingChange first = resp.getResult().get(0);
    assertEquals(2021, first.getContestId());
    assertEquals("shisukenohara", first.getHandle());
    assertEquals(4595, first.getRank());
    assertEquals(0, first.getOldRating());
    assertEquals(483, first.getNewRating());
  }
}
