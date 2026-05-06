package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LobbyUpdateEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("table_id") String tableId,
        @JsonProperty("current_players") int currentPlayers,
        @JsonProperty("max_players") int maxPlayers
) {}