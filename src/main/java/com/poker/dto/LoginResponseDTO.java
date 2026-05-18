package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.persistence.entity.Account;

public record LoginResponseDTO(
        @JsonProperty("user_id") String userId,
        @JsonProperty("login") String login,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("wallet_balance") long walletBalance,
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("daily_bonus_received") boolean dailyBonusReceived
) {
    public static LoginResponseDTO fromAccount(Account account, String accessToken,
                                               String refreshToken, boolean dailyBonusReceived) {
        return new LoginResponseDTO(
                String.valueOf(account.getId()),
                account.getLogin(),
                account.getNickname(),
                account.getBalance(),
                accessToken,
                refreshToken,
                dailyBonusReceived
        );
    }
}