package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ShowdownPayoutDTO(
        @JsonProperty("user_id") String userId,
        long amount,
        @JsonProperty("hand_name") String handName,
        @JsonProperty("rank_cards") List<String> rankCards,
        @JsonProperty("kicker_cards") List<String> kickerCards,
        @JsonProperty("is_side_pot") boolean isSidePot,
        @JsonProperty("is_kicker_winner") boolean isKickerWinner
) {}