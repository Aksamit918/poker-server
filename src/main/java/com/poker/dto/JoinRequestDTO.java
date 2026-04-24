package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinRequestDTO(
        @JsonProperty("user_id") String userId,
        long chips
) {}