package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshResponseDTO(
        @JsonProperty("access_token") String accessToken
) {}