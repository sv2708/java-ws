# Project Context: Java WebSocket Chat

This project is a real-time chat application built with Spring Boot and WebSockets.

## Tech Stack
- **Language:** Java 21+
- **Framework:** Spring Boot 3.x
- **Communication:** Spring WebSocket
- **JSON Processing:** Jackson
- **Build System:** Gradle (Kotlin DSL)
- **Features:** Virtual Threads enabled (`spring.threads.virtual.enabled: true`)

## Core Components
- `org.sv2708.Main`: Spring Boot entry point.
- `org.sv2708.config.WebsocketConfig`: Configures WebSocket handlers. The endpoint is exposed at `/ws`.
- `org.sv2708.handlers.ChatMessageHandler`: Main logic for handling WebSocket messages.
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
- **User Management:** Uses `ConcurrentHashMap` to track `activeUsers` (Handle -> Session) and `sessionToHandle` (Session ID -> Handle).
- **Handle Collision:** If a handle is taken during `JOIN`, a numeric suffix is appended.
- **Error Handling:** Invalid message formats or unknown types result in an `ERROR` message sent back to the session.

## Testing
- `org.sv2708.ChatIntegrationTest`: Integration tests for verifying chat functionality.
