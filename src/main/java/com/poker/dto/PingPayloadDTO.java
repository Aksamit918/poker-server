package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PingPayloadDTO(
        @JsonProperty("clientTime") long clientTime
) {
}
