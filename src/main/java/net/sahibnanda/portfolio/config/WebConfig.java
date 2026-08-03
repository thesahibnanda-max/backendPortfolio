package net.sahibnanda.portfolio.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures Spring MVC's JSON (de)serialization to use Jackson 2, matching
 * {@link net.sahibnanda.portfolio.utils.JsonUtils} and this app's Lombok
 * {@code @Jacksonized} POJOs, instead of the Jackson 3 converter
 * ({@code JacksonJsonHttpMessageConverter}) Spring Boot 4 selects by default
 * when both Jackson 2 and 3 are on the classpath.
 *
 * <p>
 * {@link MappingJackson2HttpMessageConverter} is itself deprecated for removal
 * in Spring Framework 7 in favor of the Jackson 3 converter above -- kept here
 * deliberately, for now, so the whole app stays on one Jackson major version
 * (2.x) rather than splitting across two. Migrating
 * {@link net.sahibnanda.portfolio.utils.JsonUtils} and every
 * {@code @Jacksonized} POJO to Jackson 3 is a separate, larger change.
 */
@Configuration(proxyBeanMethods = false)
public final class WebConfig implements WebMvcConfigurer {

  @Override
  public void configureMessageConverters(
      final HttpMessageConverters.ServerBuilder builder) {
    ObjectMapper objectMapper =
        new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    builder.withJsonConverter(
        new MappingJackson2HttpMessageConverter(objectMapper));
  }
}
