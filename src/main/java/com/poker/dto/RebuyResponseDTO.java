package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RebuyResponseDTO(
        @JsonProperty("chips") long chips,
        @JsonProperty("wallet_balance") long walletBalance
) {}