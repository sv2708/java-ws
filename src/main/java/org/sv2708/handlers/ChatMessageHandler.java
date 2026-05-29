package org.sv2708.handlers;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.sv2708.config.RedisConfig;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.sv2708.config.RedisConfig.BROADCAST_CHANNEL;
import static org.sv2708.config.RedisConfig.PRESENCE_EXPIRY_TTL;
import static org.sv2708.config.RedisConfig.PRESENCE_KEY;

public class ChatMessageHandler extends TextWebSocketHandler {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ChatMessageHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    

    // RedisTemplate to query redis. Deserializes the KV pairs to UTF-8 Strings
    @Autowired
    private StringRedisTemplate redisTemplate;

    // This instance Node's ID
    @Autowired
    private String nodeId;

    // In-Memory Handle-Session Map. <handle(username), Session>
    private final Map<String, WebSocketSession> activeUsers = new ConcurrentHashMap<>();
    
    // In-Memory Session-Handle Map. <SessionId, handle(username)
    // Received messages in websocket contains only the session id.
    // The sender handle derived from the session id.
    private final Map<String, String> sessionToHandle = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("New connection established: {} on node: {}", session.getId(), nodeId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
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
                default -> sendError(session, "Unknown message type: " + msg.type());
            }
        } catch (Exception e) {
            logger.error("Error processing message from " + session.getId(), e);
            sendError(session, "Error: " + e.getMessage());
        }
    }

    private void handleJoin(WebSocketSession session, ChatMessage msg) throws IOException {
        String requestedHandle = msg.handle(); // sender's handle
        if (requestedHandle == null || requestedHandle.isBlank()) {
            sendError(session, "Handle is required for JOIN");
            return;
        }

        String finalHandle = requestedHandle;
        int suffix = 1;

        // Check global presence in Redis, if already present, append a suffix to the handle value to be unique
        while (redisTemplate.hasKey(PRESENCE_KEY + finalHandle)) {
            finalHandle = requestedHandle + (suffix++);
        }

        // Register the joined handle in Redis with TTL (15s)
        redisTemplate.opsForValue().set(PRESENCE_KEY + finalHandle, nodeId, Duration.ofSeconds(PRESENCE_EXPIRY_TTL));

        activeUsers.put(finalHandle, session); // add to local connection map
        sessionToHandle.put(session.getId(), finalHandle); // add to session-handle map

        logger.info("User joined: {} (Session: {}) on node: {}", finalHandle, session.getId(), nodeId);
        // send joined ack message from System to sender
        sendMessage(session, new ChatMessage("SYSTEM", finalHandle, null, "Welcome! Your handle is " + finalHandle + " on node " + nodeId));
    }

    private void handleBroadcast(WebSocketSession senderSession, ChatMessage msg) throws IOException {
        String senderHandle = sessionToHandle.get(senderSession.getId());
        if (senderHandle == null) { // if sender not present in the connection map, send error
            sendError(senderSession, "You must JOIN before broadcasting.");
            return;
        }

        ChatMessage broadcastMsg = new ChatMessage("BROADCAST", senderHandle, null, msg.content());
        // broadcast message will be sent to the redis topic "chat:broadcast" with Stringified Json of the message.
        redisTemplate.convertAndSend(BROADCAST_CHANNEL, objectMapper.writeValueAsString(broadcastMsg));
    }

    private void handleDirect(WebSocketSession senderSession, ChatMessage msg) throws IOException {
        String senderHandle = sessionToHandle.get(senderSession.getId());
        if (senderHandle == null) { // if sender not present in the connection map, send error
            sendError(senderSession, "You must JOIN before sending messages.");
            return;
        }
        // find the target node to which this message needs to be sent.
        // redis key is the "presence:<targetHandleId>"
        // redis value is the node-id that holds the connection for that handle
        String targetNodeId = redisTemplate.opsForValue().get(PRESENCE_KEY + msg.to());

        // if the target handle is not mapped to any node, then they are not online.
        if (targetNodeId == null) {
            sendError(senderSession, "User " + msg.to() + " is not online.");
            return;
        }

        // construct message
        ChatMessage directMsg = new ChatMessage("DIRECT", senderHandle, msg.to(), msg.content());
        String payload = objectMapper.writeValueAsString(directMsg);

        // if the node id is local, send it to local
        if (nodeId.equals(targetNodeId)) {
            deliverLocally(directMsg);
        } else {
            // publish the message to the target node's topic "chat:node:<targetNodeId>"
            redisTemplate.convertAndSend("chat:node:" + targetNodeId, payload);
        }
    }

    /**
     * Called by RedisMessageSubscriber to deliver a message to a local user.
     */
    public void deliverLocally(ChatMessage msg) {
        try {
            if ("BROADCAST".equalsIgnoreCase(msg.type())) {
                // iterate to all local connections and send it to the client
                String payload = objectMapper.writeValueAsString(msg);
                TextMessage textMessage = new TextMessage(payload);
                for (WebSocketSession s : activeUsers.values()) {
                    var handle = sessionToHandle.getOrDefault(s.getId(), "");
                    if (s.isOpen() && !handle.equals(msg.handle())) {
                        s.sendMessage(textMessage);
                    }
                }
            } else if ("DIRECT".equalsIgnoreCase(msg.type())) {
                // get the specific local connection and send it
                WebSocketSession recipientSession = activeUsers.get(msg.to());
                if (recipientSession != null && recipientSession.isOpen()) {
                    sendMessage(recipientSession, msg);
                }
            }
        } catch (IOException e) {
            logger.error("Error in local delivery", e);
        }
    }

    @Scheduled(fixedRate = 5000)
    public void heartbeat() {
        for (String handle : activeUsers.keySet()) {
            redisTemplate.expire(PRESENCE_KEY + handle, Duration.ofSeconds(PRESENCE_EXPIRY_TTL));
        }
    }

    private void sendMessage(WebSocketSession session, ChatMessage msg) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        }
    }

    private void sendError(WebSocketSession session, String errorContent) throws IOException {
        sendMessage(session, new ChatMessage("ERROR", "SYSTEM", null, errorContent));
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

    private void removeUser(WebSocketSession session) {
        String handle = sessionToHandle.remove(session.getId());
        if (handle != null) {
            activeUsers.remove(handle);
            redisTemplate.delete(PRESENCE_KEY + handle);
            logger.info("User left: {} from node: {}", handle, nodeId);
        }
    }
}
