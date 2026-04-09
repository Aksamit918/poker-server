package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@NoArgsConstructor
public class JoinRequestDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("wallet_balance")
    private int walletBalance;

    @JsonProperty("chips")
    private int chips;

}
