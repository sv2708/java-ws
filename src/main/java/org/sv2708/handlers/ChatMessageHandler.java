package org.sv2708.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatMessageHandler extends TextWebSocketHandler {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ChatMessageHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Maps Handle -> Session
    private final Map<String, WebSocketSession> activeUsers = new ConcurrentHashMap<>();
    
    // Maps Session ID -> Handle
    private final Map<String, String> sessionToHandle = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("New connection established: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.debug("Received message from {}: {}", session.getId(), message.getPayload());
        try {
            ChatMessage msg = objectMapper.readValue(message.getPayload(), ChatMessage.class);
            if (msg == null || msg.type() == null) {
                sendError(session, "Invalid message format");
                return;
            }
            
            switch (msg.type().toUpperCase()) {
                case "JOIN" -> handleJoin(session, msg);
                case "BROADCAST" -> handleBroadcast(session, msg);
                case "DIRECT" -> handleDirect(session, msg);
                default -> {
                    logger.warn("Unknown message type: {}", msg.type());
                    sendError(session, "Unknown message type: " + msg.type());
                }
            }
        } catch (Exception e) {
            logger.error("Error processing message from " + session.getId(), e);
            sendError(session, "Error: " + e.getMessage());
        }
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeUser(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        removeUser(session);
        try { session.close(); } catch (Exception ignored) {}
    }

    private void handleJoin(WebSocketSession session, ChatMessage msg) throws IOException {
        String requestedHandle = msg.handle();
        if (requestedHandle == null || requestedHandle.isBlank()) {
            sendError(session, "Handle is required for JOIN");
            return;
        }

        String finalHandle = requestedHandle;
        int suffix = 1;

        while (activeUsers.containsKey(finalHandle)) {
            finalHandle = requestedHandle + (suffix++);
        }

        activeUsers.put(finalHandle, session);
        sessionToHandle.put(session.getId(), finalHandle);

        logger.info("User joined: {} (Session: {})", finalHandle, session.getId());
        sendMessage(session, new ChatMessage("SYSTEM", finalHandle, null, "Welcome! Your handle is " + finalHandle));
    }

    private void handleBroadcast(WebSocketSession senderSession, ChatMessage msg) throws IOException {
        String senderHandle = sessionToHandle.get(senderSession.getId());
        if (senderHandle == null) {
            sendError(senderSession, "You must JOIN before broadcasting.");
            return;
        }

        ChatMessage broadcastMsg = new ChatMessage("BROADCAST", senderHandle, null, msg.content());
        String payload = objectMapper.writeValueAsString(broadcastMsg);
        TextMessage textMessage = new TextMessage(payload);

        for (WebSocketSession s : activeUsers.values()) {
            if (s.isOpen() && !s.equals(senderSession)) {
                s.sendMessage(textMessage);
            }
        }
    }

    private void handleDirect(WebSocketSession senderSession, ChatMessage msg) throws IOException {
        String senderHandle = sessionToHandle.get(senderSession.getId());
        if (senderHandle == null) {
            sendError(senderSession, "You must JOIN before sending messages.");
            return;
        }

        WebSocketSession recipientSession = activeUsers.get(msg.to());
        if (recipientSession == null || !recipientSession.isOpen()) {
            sendError(senderSession, "User " + msg.to() + " is not online.");
            return;
        }

        ChatMessage directMsg = new ChatMessage("DIRECT", senderHandle, msg.to(), msg.content());
        sendMessage(recipientSession, directMsg);
    }

    private void sendMessage(WebSocketSession session, ChatMessage msg) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        }
    }

    private void sendError(WebSocketSession session, String errorContent) throws IOException {
        sendMessage(session, new ChatMessage("ERROR", "SYSTEM", null, errorContent));
    }

    private void removeUser(WebSocketSession session) {
        String handle = sessionToHandle.remove(session.getId());
        logger.info("User Removed: {}", handle);
        if (handle != null) {
            activeUsers.remove(handle);
            logger.info("Session removed for user: {}", handle);
        }
    }
}
