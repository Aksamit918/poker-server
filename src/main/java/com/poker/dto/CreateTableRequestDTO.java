package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateTableRequestDTO(
        @NotBlank
        String name,

        @NotBlank
        @JsonProperty("user_id")
        String userId,

        long chips,

        @Min(value = 2, message = "2 players minimum")
        @JsonProperty("min_players_num")
        int minPlayersNum,

        @Max(value = 9, message = "9 players maximum")
        @JsonProperty("max_players_num")
        int maxPlayersNum,

        @JsonProperty("small_blind")
        long smallBlind,

        @JsonProperty("big_blind")
        long bigBlind
) {}