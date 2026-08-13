package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponseDTO(
        String errorType,
        String message
) {
    @JsonProperty("error")
    public String error() {
        return message;
    }
}
