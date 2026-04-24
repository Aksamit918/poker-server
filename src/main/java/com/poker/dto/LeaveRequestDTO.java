package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LeaveRequestDTO(
        @JsonProperty("user_id") String userId,
        String token
) {}