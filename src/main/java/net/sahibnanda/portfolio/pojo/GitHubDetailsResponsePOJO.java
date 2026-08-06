package net.sahibnanda.portfolio.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import net.sahibnanda.portfolio.objects.GitHubDetails;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Jacksonized
@Getter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class GitHubDetailsResponsePOJO extends ResponsePOJO {

  /** Details for every configured GitHub account. */
  private final List<GitHubDetails> githubDetails;
}
