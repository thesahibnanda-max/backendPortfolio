package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * The portfolio owner's personality profile, fetched from a fixed, hosted JSON
 * resource.
 */
@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public final class PersonalityResponse {

  /** The portfolio owner's full personal profile. */
  private final PersonalProfile personalProfile;

  /** Appearance, personality, interests, favorites, and lifestyle. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PersonalProfile {

    /** Basic identity information. */
    private final BasicInfo basicInfo;

    /** Physical appearance details. */
    private final PhysicalAppearance physicalAppearance;

    /** Personality traits and work preferences. */
    private final Personality personality;

    /** Interests across sports, fitness, and technology. */
    private final Interests interests;

    /** Favorite movies, games, artists, and sporting icons. */
    private final Favorites favorites;

    /** Lifestyle flags. */
    private final Lifestyle lifestyle;

    /** Spoken languages and proficiency levels. */
    private final List<Language> languages;
  }

  /** Basic identity information. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class BasicInfo {

    /** Nationality. */
    private final String nationality;

    /** Gender. */
    private final String gender;

    /** Height, in both feet and centimeters. */
    private final Height height;
  }

  /** A height measurement expressed two ways. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Height {

    /** Height in whole feet. */
    private final Integer feet;

    /** Height in centimeters. */
    private final Integer centimeters;
  }

  /** Physical appearance details. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PhysicalAppearance {

    /** Overall body type. */
    private final String bodyType;

    /** Physique description. */
    private final String physique;

    /** Fitness level. */
    private final String fitnessLevel;

    /** Strongest muscle group. */
    private final String strongestMuscleGroup;

    /** Fitness goals. */
    private final List<String> fitnessGoals;

    /** Hair description. */
    private final Hair hair;

    /** Eye description. */
    private final Eyes eyes;

    /** Skin tone. */
    private final String skinTone;

    /** Facial features. */
    private final Face face;

    /** Worn accessories. */
    private final Accessories accessories;

    /** Fashion preferences. */
    private final Fashion fashion;
  }

  /** Hair description. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Hair {

    /** Hair color. */
    private final String color;

    /** Hair texture. */
    private final String texture;

    /** Hair style. */
    private final String style;
  }

  /** Eye description. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Eyes {

    /** Eye color. */
    private final String color;
  }

  /** Facial features. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Face {

    /** Face shape. */
    private final String shape;

    /** Eyebrow description. */
    private final String eyebrows;

    /** Facial hair description. */
    private final String facialHair;
  }

  /** Worn accessories. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Accessories {

    /** Whether glasses are worn. */
    private final Boolean wearsGlasses;
  }

  /** Fashion preferences. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Fashion {

    /** Fashion style. */
    private final String style;

    /** Favorite colors. */
    private final List<String> favoriteColors;
  }

  /** Personality traits and work preferences. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Personality {

    /** Core personality traits. */
    private final List<String> coreTraits;

    /** Professional traits. */
    private final List<String> professionalTraits;

    /** Work preferences. */
    private final WorkPreferences workPreferences;

    /** Personal values. */
    private final List<String> personalValues;
  }

  /** Work preferences. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class WorkPreferences {

    /** Preferred engineering domains. */
    private final List<String> preferredDomains;

    /** Valued engineering qualities. */
    private final List<String> engineeringValues;
  }

  /** Interests across sports, fitness, and technology. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Interests {

    /** Favorite team/player per sport, keyed by sport name. */
    private final Map<String, SportFavorite> sports;

    /** Fitness-related interests. */
    private final List<String> fitness;

    /** Technology-related interests. */
    private final List<String> technology;
  }

  /** A favorite team and player for a sport. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class SportFavorite {

    /** Favorite team. */
    private final String favoriteTeam;

    /** Favorite player. */
    private final String favoritePlayer;
  }

  /** Favorite movies, games, artists, and sporting icons. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Favorites {

    /** Favorite movies. */
    private final List<TitledWork> movies;

    /** Favorite games. */
    private final List<TitledWork> games;

    /** Favorite artists. */
    private final List<Artist> artists;

    /** Favorite sporting icon per sport, keyed by sport name. */
    private final Map<String, String> sportsIcons;
  }

  /** A titled work (movie or game) with its genre. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class TitledWork {

    /** Title. */
    private final String title;

    /** Genre. */
    private final String genre;
  }

  /** A favorite artist. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Artist {

    /** Artist name. */
    private final String name;

    /** Artist type, e.g. singer. */
    private final String type;
  }

  /** Lifestyle flags. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Lifestyle {

    /** Whether fitness-focused. */
    private final Boolean fitnessFocused;

    /** Whether a sports enthusiast. */
    private final Boolean sportsEnthusiast;

    /** Whether a technology enthusiast. */
    private final Boolean technologyEnthusiast;

    /** Whether continuously learning. */
    private final Boolean continuousLearning;
  }

  /** A spoken language and its proficiency level. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Language {

    /** Language name. */
    private final String name;

    /** Proficiency level. */
    private final String proficiency;
  }
}
