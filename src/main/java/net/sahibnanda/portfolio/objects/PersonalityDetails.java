package net.sahibnanda.portfolio.objects;

import lombok.Builder;
import lombok.Data;
import net.sahibnanda.portfolio.models.PersonalityResponse;

/**
 * The portfolio owner's personality profile, composed from the raw
 * {@link PersonalityResponse} plus the biography LeetCode records on its own
 * profile (a better domain fit for a personal "about me" than
 * {@link LeetcodeDetails}, which is competitive-programming stats only).
 */
@Builder
@Data
public final class PersonalityDetails {

  /** Appearance, personality, interests, favorites, and lifestyle. */
  private final PersonalityResponse.PersonalProfile personalProfile;

  /** Self-written biography, as recorded on the primary LeetCode account. */
  private final String aboutMe;
}
