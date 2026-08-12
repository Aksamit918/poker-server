package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserEmotesResponseDTO(
        @JsonProperty("wallet_balance") long walletBalance,
        List<EmoteCatalogItemDTO> catalog,
        @JsonProperty("owned_emote_ids") List<String> ownedEmoteIds
) {
}
