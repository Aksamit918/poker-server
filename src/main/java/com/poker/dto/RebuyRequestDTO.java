package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RebuyRequestDTO(
        @JsonProperty("user_id") String userId,
        long amount,
        String token
) {}