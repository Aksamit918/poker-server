package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Card;
import com.poker.model.Table;
import com.poker.model.TableStates;

import java.util.List;

public record TableDetailsDTO(
        @JsonProperty("event_type")
        String eventType,
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
        String state,
        @JsonProperty("showdown_details") ShowdownDetailsDTO showdownDetails
) {
    public static TableDetailsDTO createTableDetailsDTO(Table table,  String requestingUserId) {
        long pot = table.getPot();
        long currentMax = table.getCurrentMaxBet();
        String state = table.getState().name();

        int dealerIdx = table.getDealerIdx();
        int activePlayerIdx = table.getActivePlayerIdx();

        List<String> cardStrings = table.getCommunityCards().stream()
                .map(Card::getShortName)
                .toList();

        boolean isShowdown = table.getState() == TableStates.SHOWDOWN;

        List<PlayerDTO> playerDTOs = table.getPlayers().stream()
                .map(p -> {
                    boolean isOwner = p.getUserId().equals(requestingUserId);
                    return PlayerDTO.fromPlayer(p, currentMax, isOwner, isShowdown);
                })
                .toList();

        ShowdownDetailsDTO showdownDetails = null;
        if (table.getState() == TableStates.SHOWDOWN) {
            showdownDetails = new ShowdownDetailsDTO(table.getLastShowdownPayouts());
        }

        return new TableDetailsDTO(
                "TABLE_UPDATE",
                table.getId(),
                table.getName(),
                table.getBigBlindBet(),
                table.getMinBuyIn(),
                table.getMaxBuyIn(),
                pot,
                dealerIdx,
                activePlayerIdx,
                cardStrings,
                playerDTOs,
                state,
                showdownDetails
        );
    }
}