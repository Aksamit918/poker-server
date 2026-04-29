package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Player;
import com.poker.model.PlayerStatus;

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
        String displayStatus = player.getStatus().name();
        if (player.getStatus() == PlayerStatus.WAITING) {
            displayStatus = "SITTING_OUT";
        }

        return new PlayerPublicStateDTO(
                player.getUserId(),
                player.getName(),
                player.getSeatIndex(),
                player.getChips().get(),
                displayStatus,
                player.getRoundContribution(),
                player.canAct()
        );
    }
}