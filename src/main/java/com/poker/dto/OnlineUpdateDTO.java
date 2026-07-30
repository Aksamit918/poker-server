package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OnlineUpdateDTO(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("online_count") int onlineCount
) {
}