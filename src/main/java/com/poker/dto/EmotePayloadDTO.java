package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmotePayloadDTO(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("user_id") String userId,
        @JsonProperty("emote_id") String emoteId
) {
}
