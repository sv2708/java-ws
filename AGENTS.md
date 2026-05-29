# Project Context: Java Distributed WebSocket Chat

This project is a real-time, multi-node chat application built with Spring Boot, WebSockets, and Redis. It is designed to scale horizontally.

## Tech Stack
- **Language:** Java 25+
- **Framework:** Spring Boot 4.0.6
- **Communication:** Spring WebSocket
- **State & Messaging:** Redis (via Spring Data Redis `StringRedisTemplate`)
- **JSON Processing:** Jackson 3 (Note: Package changed to `tools.jackson.databind`)
- **Build System:** Gradle 9.2.0 (Kotlin DSL 2.2.20)
- **Features:** Virtual Threads enabled (`spring.threads.virtual.enabled: true`)

## Migration Notes (Spring Boot 4)
- **Jackson 3:** Core packages moved from `com.fasterxml.jackson` to `tools.jackson`. Most Jackson exceptions (like `JacksonException`) are now unchecked.
- **Mockito Integration:** `@MockBean` and `@SpyBean` are replaced by `@MockitoBean` and `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito`.

## Architecture Overview
The application uses a distributed architecture where Redis acts as a state store and message broker across multiple server nodes.

- **Global Presence (State):** Redis stores active user handles and maps them to the specific `nodeId` they are connected to (`presence:<handle>`). This prevents handle collisions across the cluster.
- **Message Routing (Pub/Sub):**
  - **Broadcasts:** Sent to a global Redis topic (`chat:broadcast`). All nodes subscribe and push the message to their local WebSocket clients.
  - **Direct Messages:** Routed to the specific node the recipient is connected to using a node-specific topic (`chat:node:<nodeId>`).

## Core Components
- `org.sv2708.config.WebsocketConfig`: Exposes the WebSocket endpoint at `/ws`.
- `org.sv2708.config.RedisConfig`: Configures `RedisTemplate`, `MessageListenerAdapter`, and Pub/Sub topics.
- `org.sv2708.handlers.ChatMessageHandler`: Core application logic. Manages local WebSocket sessions, handles incoming JSON messages, updates Redis presence, and routes messages. Includes a `@Scheduled` heartbeat to refresh Redis TTLs.
- `org.sv2708.handlers.RedisMessageListener`: Acts as the bridge from Redis back to the application. It receives messages from Redis Pub/Sub channels and passes them to `ChatMessageHandler` for local delivery.
- `org.sv2708.handlers.ChatMessage`: Data record for chat messages.

## Chat Protocol
Messages are exchanged as JSON. Each message has a `type` field:

| Type | Description | Fields |
| :--- | :--- | :--- |
| `JOIN` | User joins the chat with a handle. | `handle` |
| `BROADCAST` | Sends a message to all connected users. | `content` |
| `DIRECT` | Sends a private message to a specific user. | `to`, `content` |
| `SYSTEM` | System-generated notifications (e.g., welcome). | `content` |
| `ERROR` | Error notifications from the server. | `content` |

## Implementation Details
- **Local Maps:** Uses `ConcurrentHashMap` to track `activeUsers` (Handle -> Session) and `sessionToHandle` (Session ID -> Handle) for clients connected to the current node.
- **Sanitization:** When a client sends a message, the server constructs a new `ChatMessage` object, pulling the true sender's handle from the secure `sessionToHandle` map to prevent sender spoofing.
- **Heartbeat:** Nodes actively refresh the TTL of their connected users in Redis to maintain global presence. Disconnections automatically remove the presence key.
