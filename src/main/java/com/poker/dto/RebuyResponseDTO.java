package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RebuyResponseDTO(
        @JsonProperty("chips") long chips
) {}