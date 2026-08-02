package net.sahibnanda.portfolio.repository.init;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Runs the database schema initialization script on application startup.
 */
@Component
public final class SchemaInitializer implements ApplicationRunner {

  /** Classpath location of the SQL schema script to execute. */
  private static final String SCHEMA_SCRIPT_LOCATION = "db/schema.sql";

  /** jOOQ context used to execute the schema script. */
  private final DSLContext dslContext;

  /**
   * Creates a new schema initializer.
   *
   * @param jooqDslContext the jOOQ context used to run the schema script
   */
  public SchemaInitializer(final DSLContext jooqDslContext) {
    this.dslContext = jooqDslContext;
  }

  /**
   * Executes the schema script against the database on startup.
   *
   * @param args the application arguments (unused)
   */
  @Override
  public void run(final ApplicationArguments args) {
    dslContext.parser().parse(readSchemaScript()).executeBatch();
  }

  private String readSchemaScript() {
    Resource resource = new ClassPathResource(SCHEMA_SCRIPT_LOCATION);
    try (InputStream inputStream = resource.getInputStream()) {
      return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read schema script: " + SCHEMA_SCRIPT_LOCATION, e);
    }
  }
}
