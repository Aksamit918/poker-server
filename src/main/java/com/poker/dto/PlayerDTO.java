package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Card;
import com.poker.model.Player;

import java.util.Collections;
import java.util.List;

public record PlayerDTO(
        @JsonProperty("user_id") String userId,
        String name,
        @JsonProperty("seat_index") int seatIndex,
        long chips,
        List<String> cards,
        String status,
        @JsonProperty("is_active") boolean active,
        @JsonProperty("round_contribution") long roundContribution,
        @JsonProperty("amount_to_call") long amountToCall
) {
    public static PlayerDTO fromPlayer(Player player, long currentMaxBet, boolean isOwner, boolean isShowdown) {
        long toCall = Math.max(0, currentMaxBet - player.getRoundContribution());

        List<String> cards = Collections.emptyList();

        if (isOwner || isShowdown) {
            cards = player.getHand().stream()
                    .map(Card::getShortName)
                    .toList();
        }

        return new PlayerDTO(
                player.getUserId(),
                player.getName(),
                player.getSeatIndex(),
                player.getChips().get(),
                cards,
                player.getStatus().name(),
                player.canAct(),
                player.getRoundContribution(),
                toCall
        );
    }
}