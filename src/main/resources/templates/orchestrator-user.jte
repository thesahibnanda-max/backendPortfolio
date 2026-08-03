@import java.util.List
@import net.sahibnanda.portfolio.entity.Message
@param List<Message> conversationHistory
@param String currentMessage

@if(!conversationHistory.isEmpty())
Conversation so far:
@for(Message message : conversationHistory)
${message.role().name()}: ${message.message()}
@endfor

@endif
Current message:
${currentMessage}
