package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AnonymousChatCleanupServiceTest
    extends AbstractRepositoryIntegrationTest {

  @Autowired
  private AnonymousChatCleanupService cleanupService;
  @Autowired
  private AnonymousChatService anonymousChatService;
  @Autowired
  private DSLContext dslContext;

  @Test
  void purgeIdleChatsDeletesOnlyChatsIdleLongerThanTenMinutes() {
    ChatObject stale = anonymousChatService
        .createChat(StringUtils.generateUlid(), "Stale chat");
    ChatObject fresh = anonymousChatService
        .createChat(StringUtils.generateUlid(), "Fresh chat");

    dslContext.update(Tables.ANONYMOUS_CHATS)
        .set(Tables.ANONYMOUS_CHATS.UPDATED_AT,
            LocalDateTime.now().minusMinutes(11))
        .where(Tables.ANONYMOUS_CHATS.CHAT_ID.eq(stale.getChatId())).execute();

    cleanupService.purgeIdleChats();

    int staleCount = dslContext.fetchCount(Tables.ANONYMOUS_CHATS,
        Tables.ANONYMOUS_CHATS.CHAT_ID.eq(stale.getChatId()));
    int freshCount = dslContext.fetchCount(Tables.ANONYMOUS_CHATS,
        Tables.ANONYMOUS_CHATS.CHAT_ID.eq(fresh.getChatId()));
    assertThat(staleCount).isZero();
    assertThat(freshCount).isEqualTo(1);
  }
}
