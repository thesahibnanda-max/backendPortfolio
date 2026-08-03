package net.sahibnanda.portfolio.config;

import java.util.Map;
import net.sahibnanda.portfolio.enums.GroqModel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@code LLMService}'s model selection and sampling defaults.
 * Product configuration, not secret -- set directly in application.yml rather
 * than sourced from an environment variable.
 *
 * @param modelWeights relative selection weight per model; a model is chosen
 *        randomly on every call, weighted by these values
 * @param minTemperature lower bound of the random temperature range used when a
 *        call doesn't specify one
 * @param maxTemperature upper bound of the random temperature range used when a
 *        call doesn't specify one
 * @param minTopP lower bound of the random top-p range used when a call doesn't
 *        specify one
 * @param maxTopP upper bound of the random top-p range used when a call doesn't
 *        specify one
 * @param maxCompletionTokens maximum tokens Groq should generate per call
 */
@ConfigurationProperties(prefix = "llm")
public record LLMProperties(Map<GroqModel, Integer> modelWeights,
    double minTemperature, double maxTemperature, double minTopP,
    double maxTopP, int maxCompletionTokens) {

  /**
   * Defensively copies {@code modelWeights} into an immutable map so the stored
   * reference can never be mutated by external code.
   */
  public LLMProperties {
    modelWeights = modelWeights == null ? Map.of() : Map.copyOf(modelWeights);
  }
}
