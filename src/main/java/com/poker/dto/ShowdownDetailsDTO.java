package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ShowdownDetailsDTO(
        @JsonProperty("rank_cards") List<String> rankCards,
        @JsonProperty("kicker_cards") List<String> kickerCards,
        @JsonProperty("payouts") List<ShowdownPayoutDTO> payouts
) {
    private static final ShowdownDetailsDTO EMPTY =
            new ShowdownDetailsDTO(List.of(), List.of(), List.of());

    public static ShowdownDetailsDTO empty() {
        return EMPTY;
    }

    public static ShowdownDetailsDTO create(List<ShowdownPayoutDTO> payouts) {
        List<String> topRankCards = payouts.isEmpty() ? List.of() : payouts.get(0).rankCards();
        List<String> topKickerCards = payouts.isEmpty() ? List.of() : payouts.get(0).kickerCards();

        return new ShowdownDetailsDTO(topRankCards, topKickerCards, payouts);
    }
}