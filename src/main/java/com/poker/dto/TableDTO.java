package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Table;
import com.poker.util.FormatUtils;

public record TableDTO(
        @JsonProperty("table_id") String id,
        @JsonProperty("table_name") String name,
        @JsonProperty("min_players_num") int minPlayerNum,
        @JsonProperty("max_players_num") int maxPlayersNum,
        @JsonProperty("blinds") String blinds,

        @JsonProperty("min_buy_in") String minBuyIn,
        @JsonProperty("max_buy_in") String maxBuyIn,

        @JsonProperty("current_players") int currentPlayers,
        @JsonProperty("max_players") int maxPlayers,
        @JsonProperty("state") String state,

        @JsonProperty("is_private") boolean isPrivate
) {
    public static TableDTO createTableDTO(Table table) {
        return new TableDTO(
                table.getId(),
                table.getName(),
                table.getMIN_PLAYERS(),
                table.getMAX_PLAYERS(),
                FormatUtils.formatBlinds(table.getSmallBlindBet(), table.getBigBlindBet()),

                FormatUtils.format(table.getMinBuyIn()),
                FormatUtils.format(table.getMaxBuyIn()),

                table.getPlayerCount(),
                table.getMaxPlayers(),
                table.getState().name(),
                table.isPrivate()
        );
    }
}