package net.sahibnanda.portfolio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sahibnanda.portfolio.config.ProfileProperties;
import net.sahibnanda.portfolio.models.PersonalityResponse;
import net.sahibnanda.portfolio.models.ProfileResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProfileClientTest {

  private static ProfileClient profileClient;

  @BeforeAll
  static void setup() {
    profileClient = new ProfileClient(new ProfileProperties(
        "https://zaiwjonzbotyjmoghqzh.supabase.co/storage/v1/object/"
            + "public/portfolio/profile.json",
        "https://zaiwjonzbotyjmoghqzh.supabase.co/storage/v1/object/"
            + "public/portfolio/personality.json"));
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

  @Test
  void getPersonality() {
    PersonalityResponse personality = profileClient.getPersonality();
    System.out.println(personality);

    assertNotNull(personality);
    assertNotNull(personality.getPersonalProfile());

    PersonalityResponse.BasicInfo basicInfo =
        personality.getPersonalProfile().getBasicInfo();
    assertNotNull(basicInfo);
    assertEquals("Indian", basicInfo.getNationality());
    assertEquals("Male", basicInfo.getGender());
    assertNotNull(basicInfo.getHeight());
    assertEquals(6, basicInfo.getHeight().getFeet());
    assertEquals(183, basicInfo.getHeight().getCentimeters());

    assertNotNull(personality.getPersonalProfile().getPersonality());
    assertFalse(personality.getPersonalProfile().getPersonality()
        .getCoreTraits().isEmpty());

    assertNotNull(personality.getPersonalProfile().getInterests());
    assertTrue(personality.getPersonalProfile().getInterests().getSports()
        .containsKey("football"));

    assertNotNull(personality.getPersonalProfile().getLanguages());
    assertFalse(personality.getPersonalProfile().getLanguages().isEmpty());
  }
}
