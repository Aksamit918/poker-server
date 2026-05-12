package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ShowdownPayoutDTO(
        @JsonProperty("user_id") String userId,
        long amount,
        @JsonProperty("hand_name") String handName,
        @JsonProperty("winning_cards") List<String> winningCards,
        @JsonProperty("is_side_pot") boolean isSidePot
) {}