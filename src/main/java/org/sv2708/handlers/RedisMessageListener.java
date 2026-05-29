package org.sv2708.handlers;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RedisMessageListener implements MessageListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatMessageHandler chatMessageHandler;

    // Use @Lazy to avoid circular dependency since ChatMessageHandler might inject RedisTemplate
    public RedisMessageListener(@Lazy ChatMessageHandler chatMessageHandler) {
        this.chatMessageHandler = chatMessageHandler;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatMessage chatMessage = objectMapper.readValue(message.getBody(), ChatMessage.class);
            chatMessageHandler.deliverLocally(chatMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
