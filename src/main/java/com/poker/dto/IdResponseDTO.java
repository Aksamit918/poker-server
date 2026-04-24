package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IdResponseDTO(
        @JsonProperty("table_id")
        String id
) {}