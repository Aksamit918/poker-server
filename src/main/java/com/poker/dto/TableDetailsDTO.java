package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Card;
import com.poker.model.Table;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TableDetailsDTO {
    @JsonProperty("table_id")
    private String tableId;

    @JsonProperty("table_name")
    private String name;

    @JsonProperty("pot")
    private long pot;

    @JsonProperty("dealer_seat")
    private int dealerIdx;

    @JsonProperty("current_turn_seat")
    private int activePlayerIdx;

    @JsonProperty("community_cards")
    private List<String> communityCards;

    @JsonProperty("players")
    private List<PlayerDTO> players;

    @JsonProperty("state")
    private String state;

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

        return new TableDetailsDTO(table.getId(), table.getName(), pot, dealerIdx, activePlayerIdx, cardStrings, playerDTOs, state);
    }
}