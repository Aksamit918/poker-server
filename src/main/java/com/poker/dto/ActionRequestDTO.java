package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.ActionType;

public record ActionRequestDTO(
        @JsonProperty("user_id") String userId,
        ActionType type,
        long amount
) {}