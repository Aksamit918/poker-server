package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateTableRequestDTO(
        @NotBlank
        @Size(max = 20)
        String name,

        String passcode,

        @NotBlank
        @JsonProperty("user_id")
        String userId,

        long chips,

        @Min(value = 2, message = "2 players minimum")
        @JsonProperty("min_players_num")
        int minPlayersNum,

        @Min(value = 2, message = "2 players minimum")
        @Max(value = 10, message = "10 players maximum")
        @JsonProperty("max_players_num")
        int maxPlayersNum,

        @JsonProperty("small_blind")
        long smallBlind,

        @JsonProperty("big_blind")
        long bigBlind
) {
    private static final Set<Integer> ALLOWED_TABLE_SIZES = Set.of(2, 4, 6, 9, 10);

    @AssertTrue(message = "max_players_num must be 2, 4, 6, 9 or 10")
    public boolean isAllowedTableSize() {
        return ALLOWED_TABLE_SIZES.contains(maxPlayersNum);
    }
}
