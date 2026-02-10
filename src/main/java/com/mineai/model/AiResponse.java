package com.mineai.model;

import java.util.List;

/**
 * Immutable record representing the AI's response.
 * Read from JSON files in the responses directory.
 */
public record AiResponse(
    String player,
    String response,
    List<String> commands,
    long timestamp
) {}
