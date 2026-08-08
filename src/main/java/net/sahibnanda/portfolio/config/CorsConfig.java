package net.sahibnanda.portfolio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables CORS for every origin, method, and header, on every endpoint --
 * deliberately maximally permissive, not a restricted allowlist. No {@code
 * spring-boot-starter-security} is on the classpath (only {@code
 * spring-security-crypto}, for token encryption -- see {@link AuthProperties}),
 * so this is plain Spring MVC CORS via
 * {@link WebMvcConfigurer#addCorsMappings}, not Spring Security's CORS
 * integration.
 *
 * <p>
 * {@code allowedOriginPatterns("*")} (not {@code allowedOrigins("*")}) is used
 * so this keeps working unchanged if credentialed requests are ever introduced
 * later -- Spring rejects the literal-{@code *} form outright once
 * {@code allowCredentials(true)} is set, while the pattern form does not.
 * Nothing here relies on cookies today: the anonymous-session id (see the
 * {@code middleware} package) travels as a plain {@code X-Session-Id} header,
 * the same way {@code X-Auth-Token} already does, specifically so it isn't
 * subject to third-party-cookie blocking (Safari ITP, strict-mode Firefox) now
 * that the frontend and this API live on different registrable domains.
 *
 * <p>
 * {@code exposedHeaders("*")} is the fix for the bug that motivated this class:
 * {@code X-Auth-Token} (and now {@code X-Session-Id}) are non-safelisted
 * response headers, invisible to browser JS unless explicitly exposed, even
 * under an otherwise fully open policy -- an open {@code allowedHeaders} only
 * covers what the browser may send, not what JS may read back.
 */
@Configuration(proxyBeanMethods = false)
public final class CorsConfig implements WebMvcConfigurer {

  /** How long a browser may cache a preflight response, in seconds. */
  private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600;

  @Override
  public void addCorsMappings(final CorsRegistry registry) {
    registry.addMapping("/**").allowedOriginPatterns("*").allowedMethods("*")
        .allowedHeaders("*").exposedHeaders("*")
        .maxAge(PREFLIGHT_MAX_AGE_SECONDS);
  }
}
