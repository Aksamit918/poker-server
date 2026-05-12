package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LeaveResponseDTO (
    @JsonProperty("wallet_balance") long walletBalance
) {}
