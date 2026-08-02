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

@Component
public class SchemaInitializer implements ApplicationRunner {

  private static final String SCHEMA_SCRIPT_LOCATION = "db/schema.sql";

  private final DSLContext dslContext;

  public SchemaInitializer(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  @Override
  public void run(ApplicationArguments args) {
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
