package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.persistence.entity.Account;

public record LoginResponseDTO(
        @JsonProperty("user_id") String userId,
        String login,
        String nickname,
        @JsonProperty("wallet_balance")
        long walletBalance,
        String token
) {
    public static LoginResponseDTO fromAccount(Account account, String token) {
        return new LoginResponseDTO(
                String.valueOf(account.getId()),
                account.getLogin(),
                account.getNickname(),
                account.getBalance(),
                token
        );
    }
}