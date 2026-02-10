package com.mineai.model;

/**
 * Immutable record representing a player's request to the AI.
 * Serialized to JSON and written to the requests directory.
 */
public record AiRequest(
    String id,
    String player,
    String rank,
    String message,
    long timestamp
) {}
