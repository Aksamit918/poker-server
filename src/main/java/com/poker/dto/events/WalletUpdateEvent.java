package com.poker.dto.events;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WalletUpdateEvent(
        @JsonProperty("user_id") String userId,
        @JsonProperty("new_balance") long newBalance,
        String reason
) {}