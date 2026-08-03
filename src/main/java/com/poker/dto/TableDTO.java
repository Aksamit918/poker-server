package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Table;

public record TableDTO(
        @JsonProperty("table_id") String tableId,
        @JsonProperty("table_name") String tableName,
        @JsonProperty("small_blind") long smallBlind,
        @JsonProperty("big_blind") long bigBlind,
        @JsonProperty("blinds") String blinds,
        @JsonProperty("min_players") int minPlayers,
        @JsonProperty("max_players") int maxPlayers,
        @JsonProperty("current_players") int currentPlayers,
        @JsonProperty("min_buy_in") long minBuyIn,
        @JsonProperty("max_buy_in") long maxBuyIn,
        @JsonProperty("is_private") boolean isPrivate
) {
    public static TableDTO createTableDTO(Table table) {
        return new TableDTO(
                table.getId(),
                table.getName(),
                table.getSmallBlindBet(),
                table.getBigBlindBet(),
                table.getSmallBlindBet() + "/" + table.getBigBlindBet(),
                table.getMIN_PLAYERS(),
                table.getMAX_PLAYERS(),
                table.getPlayerCount(),
                table.getMinBuyIn(),
                table.getMaxBuyIn(),
                table.isPrivate()
        );
    }
}