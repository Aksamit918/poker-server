package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.ActionType;

public record PlayerActionEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("table_id") String tableId,
        @JsonProperty("seat_index") int seatIndex,
        @JsonProperty("action_type") ActionType actionType,
        long amount,
        @JsonProperty("player_state") PlayerPublicStateDTO playerState,
        @JsonProperty("total_pot") long totalPot,

        @JsonProperty("current_turn_seat") int currentTurnSeat,
        @JsonProperty("time_to_act_ms") long timeToActMs
) {}