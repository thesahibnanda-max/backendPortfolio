package net.sahibnanda.portfolio.repository;

import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;

public interface ChatRepository {

  ChatEntity create(String chatId, String username, String chatTitle, List<Message> messages);

  Optional<ChatEntity> findByChatId(String chatId);

  List<ChatEntity> findChats(String username);

  void saveMessages(String chatId, List<Message> messages);

  void delete(String chatId);

  void updateChatTitle(String chatId, String title);
}
