package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Card;
import com.poker.model.Player;
import com.poker.model.PlayerStatus; // Убедись, что импортировал статус
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PlayerDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("seat_index")
    private int seatIndex;

    @JsonProperty("chips")
    private long chips;

    @JsonProperty("cards")
    private List<String> cards;

    @JsonProperty("status")
    private String status;

    @JsonProperty("is_active")
    private boolean active;

    @JsonProperty("amount_to_call")
    private long amountToCall;

    public static PlayerDTO fromPlayer(Player player, long currentMaxBet) {
        long toCall = Math.max(0, currentMaxBet - player.getRoundContribution());

        List<String> cards = player.getHand().stream()
                .map(Card::getShortName)
                .toList();

        return new PlayerDTO(
                player.getUserId(),
                player.getName(),
                player.getSeatIndex(),
                player.getChips().get(),
                cards,
                player.getStatus().name(),
                player.getStatus() != PlayerStatus.FOLDED,
                toCall
        );
    }
}