package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Table;

public record TableDTO(
        @JsonProperty("table_id") String id,
        @JsonProperty("table_name") String name,
        @JsonProperty("min_players_num") int minPlayerNum,
        @JsonProperty("max_players_num") int maxPlayersNum,
        @JsonProperty("blinds") String blinds,
        @JsonProperty("min_buy_in") long minBuyIn,
        @JsonProperty("current_players") int currentPlayers,
        @JsonProperty("max_players") int maxPlayers,
        String state
) {
    public static TableDTO createTableDTO(Table table) {
        return new TableDTO(
                table.getId(),
                table.getName(),
                table.getMIN_PLAYERS(),
                table.getMAX_PLAYERS(),
                table.getSmallBlindBet() + "/" + table.getBigBlindBet(),
                table.getMinBuyIn(),
                table.getPlayerCount(),
                table.getMaxPlayers(),
                table.getState().name()
        );
    }
}