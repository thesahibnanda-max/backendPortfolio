package net.sahibnanda.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public final class BackendPortfolioApplication {

  private BackendPortfolioApplication() {
  }

  /**
   * Starts the Spring Boot application.
   *
   * @param args the command line arguments
   */
  public static void main(final String[] args) {
    SpringApplication.run(BackendPortfolioApplication.class, args);
  }
}
