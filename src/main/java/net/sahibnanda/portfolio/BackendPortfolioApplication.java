package net.sahibnanda.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.sahibnanda.portfolio.utils.EnvironmentUtils;

@ConfigurationPropertiesScan
@EnableScheduling
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
    EnvironmentUtils.loadDotenv();

    SpringApplication.run(BackendPortfolioApplication.class, args);
  }
}
