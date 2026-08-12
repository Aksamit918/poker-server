package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record EmotePurchaseRequestDTO(
        @NotBlank
        @JsonProperty("emote_id") String emoteId
) {
}
