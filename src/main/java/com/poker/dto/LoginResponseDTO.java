package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.persistence.entity.Account;

public record LoginResponseDTO(
        @JsonProperty("user_id") String userId,
        @JsonProperty("email") String login,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("wallet_balance") long walletBalance,
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("daily_bonus_received") boolean dailyBonusReceived,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("is_new_user") boolean isNewUser
) {
    public static LoginResponseDTO fromAccount(Account account, String accessToken,
                                               String refreshToken, boolean dailyBonusReceived, boolean isNewUser) {

        String fullAvatarUrl = null;
        String filename = account.getAvatarFilename();

        if (filename != null && !filename.isEmpty()) {
            if (filename.startsWith("http")) {
                fullAvatarUrl = filename;
            } else {
                fullAvatarUrl = "/avatars/" + filename;
            }
        }

        return new LoginResponseDTO(
                String.valueOf(account.getId()),
                account.getEmail(),
                account.getNickname(),
                account.getBalance(),
                accessToken,
                refreshToken,
                dailyBonusReceived,
                fullAvatarUrl,
                isNewUser
        );
    }
}