package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmoteCatalogItemDTO(
        @JsonProperty("emote_id") String emoteId,
        String name,
        String emoji,
        long price,
        @JsonProperty("is_default") boolean isDefault,
        boolean owned
) {
}
