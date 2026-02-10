package com.mineai.model;

/**
 * Immutable record representing an in-game event (join, quit, death, etc.)
 * logged for the AI to consume.
 */
public record GameEvent(
    String type,
    String player,
    String details,
    long timestamp
) {}
