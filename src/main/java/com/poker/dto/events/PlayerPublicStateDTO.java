package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Player;

public record PlayerPublicStateDTO(
        @JsonProperty("user_id") String userId,
        String name,
        @JsonProperty("seat_index") int seatIndex,
        long chips,
        String status,
        @JsonProperty("round_contribution") long roundContribution,
        @JsonProperty("is_active") boolean active
) {
    public static PlayerPublicStateDTO fromPlayer(Player player) {
        return new PlayerPublicStateDTO(
                player.getUserId(),
                player.getName(),
                player.getSeatIndex(),
                player.getChips().get(),
                player.getStatus().name(),
                player.getRoundContribution(),
                player.canAct()
        );
    }
}