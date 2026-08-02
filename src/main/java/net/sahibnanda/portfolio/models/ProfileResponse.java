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
 * The portfolio owner's profile document, fetched from a fixed, hosted JSON
 * resource.
 */
@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public final class ProfileResponse {

  /** Basic identity details for the portfolio owner. */
  private final ProfileDetails profileDetails;

  /** Notable projects the portfolio owner has worked on. */
  private final List<Project> projects;

  /** Languages the portfolio owner speaks. */
  private final List<String> languages;

  /** Notable achievements and certifications. */
  private final List<String> achievements;

  /** Professional work experience, most relevant first. */
  private final List<Experience> experience;

  /** Educational background. */
  private final List<Education> education;

  /** Skills grouped by category name. */
  private final Map<String, List<String>> skillsByCategory;

  /** Basic identity details for the portfolio owner. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ProfileDetails {

    /** The portfolio owner's full name. */
    private final String name;

    /** The portfolio owner's contact email address. */
    private final String email;
  }

  /** A single project entry. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Project {

    /** The project's name. */
    private final String name;

    /** The year the project was worked on. */
    private final Integer year;

    /** Bullet-point descriptions of the project. */
    private final List<String> description;

    /** Technologies used in the project. */
    private final List<String> technologies;
  }

  /** A single work experience entry. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Experience {

    /** The employer's name. */
    private final String company;

    /** The employer's location. */
    private final String location;

    /** The employment type, e.g. {@code Full-time} or {@code Internship}. */
    private final String employmentType;

    /** The job title held. */
    private final String title;

    /** The start date, in {@code YYYY-MM} form. */
    private final String startDate;

    /** The end date, in {@code YYYY-MM} form. */
    private final String endDate;

    /** Bullet-point descriptions of the role. */
    private final List<String> description;

    /** Technologies used in the role. */
    private final List<String> technologies;
  }

  /** A single education entry. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Education {

    /** The institution's name. */
    private final String institution;

    /** The degree earned. */
    private final String degree;

    /** The field of study. */
    private final String field;

    /** The start date, in {@code YYYY-MM} form. */
    private final String startDate;

    /** The end date, in {@code YYYY-MM} form. */
    private final String endDate;

    /** The grade or score achieved. */
    private final String grade;
  }
}
