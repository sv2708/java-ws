package org.sv2708.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.sv2708.handlers.RedisMessageListener;

import java.util.UUID;

@Configuration
public class RedisConfig {

    public static final String BROADCAST_CHANNEL = "chat:broadcast";
    
    private final String nodeId = UUID.randomUUID().toString();

    @Bean
    public String nodeId() {
        return nodeId;
    }

    @Bean
    public ChannelTopic nodeTopic() {
        return new ChannelTopic("chat:node:" + nodeId);
    }

    @Bean
    public ChannelTopic broadcastTopic() {
        return new ChannelTopic(BROADCAST_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter messageListenerAdapter,
                                                        ChannelTopic nodeTopic,
                                                        ChannelTopic broadcastTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListenerAdapter, nodeTopic); // listens to THIS node's topic
        container.addMessageListener(messageListenerAdapter, broadcastTopic); // listens to broadcast topic
        return container;
    }

    @Bean
    // This adapter class converts the MessageListener to the target instance "RedisMessageListener"
    public MessageListenerAdapter messageListenerAdapter(RedisMessageListener listener) {
        // messages will be passed to the listener instance
        return new MessageListenerAdapter(listener, "onMessage");
    }
}
