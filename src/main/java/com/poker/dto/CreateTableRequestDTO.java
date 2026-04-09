package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateTableRequestDTO {

    @Min(value = 2, message = "2 players minimum")
    @JsonProperty("min_players_num")
    private int minPlayersNum;

    @Max(value = 9, message = "9 players maximum")
    @JsonProperty("max_players_num")
    private int maxPlayersNum;

    @JsonProperty("small_blind")
    private int smallBlind;

    @JsonProperty("big_blind")
    private int bigBlind;
}
