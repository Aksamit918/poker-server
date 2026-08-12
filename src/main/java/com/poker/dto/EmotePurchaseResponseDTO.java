package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EmotePurchaseResponseDTO(
        String status,
        @JsonProperty("emote_id") String emoteId,
        @JsonProperty("price_paid") long pricePaid,
        @JsonProperty("wallet_balance") long walletBalance,
        @JsonProperty("owned_emote_ids") List<String> ownedEmoteIds
) {
}
