package net.sahibnanda.portfolio.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import net.sahibnanda.portfolio.objects.ProfessionalDetails;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Jacksonized
@Getter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class ProfessionalDetailsResponsePOJO extends ResponsePOJO {

  /** The portfolio owner's professional links. */
  private final ProfessionalDetails professionalDetails;
}
