package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Table;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TableDTO {
    @JsonProperty("table_id")
    private String id;

    @JsonProperty("table_name")
    private String name;

    @JsonProperty("min_players_num")
    private int minPlayerNum;

    @JsonProperty("max_players_num")
    private int maxPlayersNum;

    @JsonProperty("blinds")
    private String blinds; // e.g., "10/20"

    @JsonProperty("min_buy_in")
    private long minBuyIn;

    @JsonProperty("current_players")
    private int currentPlayers;

    @JsonProperty("max_players")
    private int maxPlayers;

    @JsonProperty("state")
    private String state;

    public static TableDTO createTableDTO(Table table) {
        String id = table.getId();
        String name = table.getName();
        int minPlayersNum = table.getMIN_PLAYERS();
        int maxPlayersNum = table.getMAX_PLAYERS();
        String blins = table.getSmallBlindBet() + "/" + table.getBigBlindBet();
        long minBuyIn = table.getMinBuyIn();
        int currentPlayers = table.getPlayerCount();
        int maxPlayers = table.getMaxPlayers();
        String state = table.getState().name();
        return new TableDTO(id, name, minPlayersNum, maxPlayersNum, blins, minBuyIn, currentPlayers, maxPlayers, state);
    }
}
