package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.ActionType;

public record PlayerActionEvent(
        @JsonProperty("table_id") String tableId,
        @JsonProperty("action_type") ActionType actionType,
        long amount,

        @JsonProperty("player_state") PlayerPublicStateDTO playerState,
        @JsonProperty("total_pot") long totalPot
) {}