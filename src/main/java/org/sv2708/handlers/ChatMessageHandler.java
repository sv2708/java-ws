package org.sv2708.handlers;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMessageHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions =
            ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession senderSession, TextMessage message)
            throws Exception {
            broadcast(senderSession, message);
    }

    private void broadcast(WebSocketSession senderSession, TextMessage message) throws IOException {
        for (WebSocketSession s : sessions) {
            if (s.isOpen() && !s.equals(senderSession)) {
                s.sendMessage(message);
            }
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(
            WebSocketSession session, Throwable error) {
        sessions.remove(session);
        try { session.close(); } catch (Exception ignored) {}
    }
}