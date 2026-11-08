package com.springchat.springchat_chat.modules.chat.mapper;

import java.util.List;
import com.springchat.springchat_chat.modules.users.mapper.UserMapper;
import com.springchat.springchat_chat.modules.chat.dto.response.ConversationDetailResponse;
import com.springchat.springchat_chat.modules.chat.dto.response.MessageResponse;
import com.springchat.springchat_chat.modules.chat.entity.Conversation;
import com.springchat.springchat_chat.modules.chat.entity.ConversationParticipant;
import com.springchat.springchat_chat.modules.chat.entity.ConversationParticipantId;
import com.springchat.springchat_chat.modules.chat.entity.Message;
import com.springchat.springchat_chat.modules.users.entity.User;
public final class ChatMapper {
    private ChatMapper() {}
    public static MessageResponse toResponse(Message message) {
        return new MessageResponse(message.getId(), message.getConversation().getId(),
                message.getSender().getId(), message.getContent(), message.getCreatedAt());
    }
    public static Message toEntity(Conversation conversation, User sender, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        return message;
    }

    public static Conversation newConversation(String type, String name) {
        Conversation conversation = new Conversation();
        conversation.setType(type);
        conversation.setName(name);
        return conversation;
    }
    public static ConversationParticipant newParticipant(Conversation conversation, User user) {
        var participant = new ConversationParticipant();
        participant.setId(new ConversationParticipantId(conversation.getId(), user.getId()));
        participant.setConversation(conversation);
        participant.setUser(user);
        return participant;
    }

    public static ConversationDetailResponse toDetailResponse(
            Conversation conversation, User other, List<User> members) {
        return new ConversationDetailResponse(
                conversation.getId(), conversation.getType(),
                other == null ? null : UserMapper.toResponse(other),
                conversation.getCreatedAt(), conversation.getName(),
                members.stream().map(UserMapper::toResponse).toList());
    }
}