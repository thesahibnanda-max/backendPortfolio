package net.sahibnanda.portfolio.repository.init;

import static org.assertj.core.api.Assertions.assertThatCode;

import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;

class SchemaInitializerTest extends AbstractRepositoryIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired private SchemaInitializer schemaInitializer;

  @Test
  void tablesExistAfterContextStartup() {
    Integer userCount = dslContext.fetchCount(Tables.USERS);
    Integer chatCount = dslContext.fetchCount(Tables.CHATS);

    org.assertj.core.api.Assertions.assertThat(userCount).isZero();
    org.assertj.core.api.Assertions.assertThat(chatCount).isZero();
  }

  @Test
  void runningInitializerAgainIsIdempotent() {
    assertThatCode(() -> schemaInitializer.run(null)).doesNotThrowAnyException();
    assertThatCode(() -> schemaInitializer.run(null)).doesNotThrowAnyException();
  }
}
