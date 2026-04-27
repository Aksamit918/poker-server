package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerStatusEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("table_id") String tableId,
        @JsonProperty("seat_index") int seatIndex,
        String status,
        String nickname
) {}