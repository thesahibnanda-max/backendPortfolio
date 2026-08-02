package net.sahibnanda.portfolio.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatEntity {
  String chatId;
  String username;
  String chatTitle;
  List<Message> messages;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}
