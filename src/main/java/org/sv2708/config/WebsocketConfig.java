package org.sv2708.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.sv2708.handlers.ChatMessageHandler;

@EnableWebSocket
@Configuration
public class WebsocketConfig  implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler(), "/ws");

    }

    @Bean
    public ChatMessageHandler chatHandler() {
        return new ChatMessageHandler();
    }
}
