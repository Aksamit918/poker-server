package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.dto.PlayerDTO;
import com.poker.dto.ShowdownDetailsDTO;
import com.poker.model.Card;
import com.poker.model.Table;
import com.poker.model.TableStates;
import com.poker.util.FormatUtils;

import java.util.List;

public record TableDetailsDTO(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("table_id") String tableId,
        @JsonProperty("table_name") String name,

        @JsonProperty("big_blind") long bigBlind,

        @JsonProperty("min_buy_in") long minBuyIn,
        @JsonProperty("max_buy_in") long maxBuyIn,
        @JsonProperty("min_buy_in_formatted") String minBuyInFmt,
        @JsonProperty("max_buy_in_formatted") String maxBuyInFmt,

        @JsonProperty("pot") long pot,
        @JsonProperty("dealer_seat") int dealerIdx,
        @JsonProperty("current_turn_seat") int activePlayerIdx,

        @JsonProperty("time_to_act_ms") long timeToActMs,

        @JsonProperty("skip_animations") Boolean skipAnimations,

        @JsonProperty("community_cards") List<String> communityCards,
        @JsonProperty("players") List<PlayerDTO> players,
        @JsonProperty("state") String state,
        @JsonProperty("showdown_details") ShowdownDetailsDTO showdownDetails
) {
    public static TableDetailsDTO createTableDetailsDTO(Table table, String requestingUserId) {
        return createTableDetailsDTO(table, requestingUserId, false);
    }

    public static TableDetailsDTO createTableDetailsDTO(Table table, String requestingUserId, boolean isSnapshot) {
        long pot = table.getPot();
        long currentMax = table.getCurrentMaxBet();
        String state = table.getState().name();

        List<String> cardStrings = table.getCommunityCards().stream()
                .map(c -> c.getShortName().toUpperCase())
                .toList();

        boolean isShowdown = table.getState() == TableStates.SHOWDOWN;

        List<PlayerDTO> playerDTOs = table.getPlayers().stream()
                .map(p -> {
                    boolean isOwner = requestingUserId != null && requestingUserId.equals(p.getUserId());
                    boolean isDealer = (p.getSeatIndex() == table.getDealerIdx());

                    return PlayerDTO.fromPlayer(p, currentMax, isOwner, isShowdown, isDealer);
                })
                .toList();

        ShowdownDetailsDTO showdownDetails = null;
        if (table.getState() == TableStates.SHOWDOWN || table.getState() == TableStates.CLEANUP) {
            showdownDetails = ShowdownDetailsDTO.create(table.getLastShowdownPayouts());
        }

        long timeToActMs = 0;
        if (table.getActivePlayerIdx() != -1 && !isShowdown && table.getState() != TableStates.WAITING_FOR_PLAYERS) {
            long totalTurnTime = 15000;
            long timeElapsed = System.currentTimeMillis() - table.getTurnStartTime();
            timeToActMs = Math.max(0, totalTurnTime - timeElapsed);
        }

        return new TableDetailsDTO(
                "TABLE_UPDATE",
                table.getId(),
                table.getName(),
                table.getBigBlindBet(),

                table.getMinBuyIn(),
                table.getMaxBuyIn(),
                FormatUtils.format(table.getMinBuyIn()),
                FormatUtils.format(table.getMaxBuyIn()),

                pot,
                table.getDealerIdx(),
                table.getActivePlayerIdx(),
                timeToActMs,
                isSnapshot,
                cardStrings,
                playerDTOs,
                state,
                showdownDetails
        );
    }
}