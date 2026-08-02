package net.sahibnanda.portfolio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sahibnanda.portfolio.config.ProfileProperties;
import net.sahibnanda.portfolio.models.ProfileResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProfileClientTest {

  private static ProfileClient profileClient;

  @BeforeAll
  static void setup() {
    profileClient = new ProfileClient(new ProfileProperties(
        "https://zaiwjonzbotyjmoghqzh.supabase.co/storage/v1/object/"
            + "public/portfolio/profile.json"));
  }

  @Test
  void getProfile() {
    ProfileResponse profile = profileClient.getProfile();
    System.out.println(profile);

    assertNotNull(profile);

    assertNotNull(profile.getProfileDetails());
    assertEquals("Sahib Nanda", profile.getProfileDetails().getName());
    assertEquals("sabbykabby12@gmail.com",
        profile.getProfileDetails().getEmail());

    assertNotNull(profile.getProjects());
    assertFalse(profile.getProjects().isEmpty());

    assertNotNull(profile.getEducation());
    assertFalse(profile.getEducation().isEmpty());

    assertNotNull(profile.getSkillsByCategory());
    assertTrue(profile.getSkillsByCategory().containsKey("Languages"));
  }
}
