package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Card;
import com.poker.model.Table;
import java.util.List;

public record TableDetailsDTO(
        @JsonProperty("table_id")
        String tableId,
        @JsonProperty("table_name")
        String name,
        @JsonProperty("big_blind") long bigBlind,
        @JsonProperty("min_buy_in") long minBuyIn,
        @JsonProperty("max_buy_in") long maxBuyIn,
        long pot,
        @JsonProperty("dealer_seat")
        int dealerIdx,
        @JsonProperty("current_turn_seat")
        int activePlayerIdx,
        @JsonProperty("community_cards")
        List<String> communityCards,
        List<PlayerDTO> players,
        String state
) {
    public static TableDetailsDTO createTableDetailsDTO(Table table) {
        long pot = table.getPot();
        long currentMax = table.getCurrentMaxBet();
        String state = table.getState().name();

        int dealerIdx = table.getDealerIdx();
        int activePlayerIdx = table.getActivePlayerIdx();

        List<String> cardStrings = table.getCommunityCards().stream()
                .map(Card::getShortName)
                .toList();

        List<PlayerDTO> playerDTOs = table.getPlayers().stream()
                .map(p -> PlayerDTO.fromPlayer(p, currentMax))
                .toList();

        return new TableDetailsDTO(
                table.getId(),
                table.getName(),
                table.getBigBlindBet(),
                table.getMinBuyIn(),
                table.getMaxBuyIn(),
                pot, dealerIdx,
                activePlayerIdx,
                cardStrings,
                playerDTOs,
                state);
    }
}

