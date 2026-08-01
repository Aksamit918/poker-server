package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Card;
import com.poker.model.Table;

import java.util.List;

public record StreetEndDTO(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("table_id") String tableId,
        @JsonProperty("state") String state,
        @JsonProperty("previous_state") String previousState,
        @JsonProperty("pot") long pot,
        @JsonProperty("contributions") List<PlayerContributionDTO> contributions,
        @JsonProperty("community_cards") List<String> communityCards,
        @JsonProperty("current_turn_seat") int activePlayerIdx,
        @JsonProperty("time_to_act_ms") long timeToActMs
) {
    public record PlayerContributionDTO(
            @JsonProperty("user_id") String userId,
            @JsonProperty("amount") long amount
    ) {}

    public static StreetEndDTO createStreetEndDTO(Table table, String previousState, List<PlayerContributionDTO> contributions) {
        List<String> cardStrings = table.getCommunityCards().stream()
                .map(Card::getShortName)
                .toList();

        long timeToActMs = 0;
        if (table.getActivePlayerIdx() != -1 && table.getState() != com.poker.model.TableStates.SHOWDOWN) {
            long timeElapsed = System.currentTimeMillis() - table.getTurnStartTime();
            timeToActMs = Math.max(0, 15000 - timeElapsed);
        }

        return new StreetEndDTO(
                "STREET_END",
                table.getId(),
                table.getState().name(),
                previousState,
                table.getPot(),
                contributions,
                cardStrings,
                table.getActivePlayerIdx(),
                timeToActMs
        );
    }
}