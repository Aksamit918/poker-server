package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Card;
import com.poker.model.Player;
import com.poker.model.PlayerStatus;

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
        @JsonProperty("amount_to_call") long amountToCall,
        @JsonProperty("sit_out_deadline") long sitOutDeadline,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("is_dealer") boolean isDealer
) {
    public static PlayerDTO fromPlayer(Player player, long currentMaxBet, boolean isOwner, boolean isShowdown, boolean isDealer) {
        long toCall = Math.max(0, currentMaxBet - player.getRoundContribution());
        if (toCall > player.getChips().get()) {
            toCall = player.getChips().get();
        }

        List<String> cards = Collections.emptyList();

        if (player.getHand() != null && !player.getHand().isEmpty()) {
            if (isOwner || isShowdown) {
                cards = player.getHand().stream()
                        .map(Card::getShortName)
                        .toList();
            } else {
                cards = List.of("card_back", "card_back");
            }
        }

        String displayStatus = player.getStatus().name();
        if (player.getStatus() == PlayerStatus.WAITING) {
            displayStatus = "SITTING_OUT";
        }

        String fullAvatarUrl = null;
        if (player.getAvatarFilename() != null && !player.getAvatarFilename().isEmpty()) {
            if (player.getAvatarFilename().startsWith("http")) {
                fullAvatarUrl = player.getAvatarFilename();
            } else {
                fullAvatarUrl = "/avatars/" + player.getAvatarFilename();
            }
        }

        return new PlayerDTO(
                player.getUserId(),
                player.getName(),
                player.getSeatIndex(),
                player.getChips().get(),
                cards,
                displayStatus,
                player.canAct(),
                player.getRoundContribution(),
                toCall,
                player.getSitOutDeadline(),
                fullAvatarUrl,
                isDealer
        );
    }
}