package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RebuyRequestDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("amount")
    private int amount;
}