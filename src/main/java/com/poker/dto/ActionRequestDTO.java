package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.ActionType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ActionRequestDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("type")
    private ActionType type;

    @JsonProperty("amount")
    private int amount;
}
