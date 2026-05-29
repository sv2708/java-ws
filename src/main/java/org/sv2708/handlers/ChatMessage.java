package org.sv2708.handlers;

/**
 * Represents a structured chat message payload.
 * 
 * @param type The type of message: JOIN, BROADCAST, DIRECT, ERROR, or SYSTEM.
 * @param handle The sender's handle or requested handle.
 * @param to The recipient's handle (for DIRECT messages).
 * @param content The message content or error description.
 */
public record ChatMessage(
    String type,
    String handle,
    String to,
    String content
) {}
