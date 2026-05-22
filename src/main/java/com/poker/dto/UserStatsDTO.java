package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.persistence.entity.Account;

public record UserStatsDTO(
        @JsonProperty("hands_played") int handsPlayed,
        @JsonProperty("total_won") long totalWon,
        @JsonProperty("biggest_pot") long biggestPot,
        @JsonProperty("win_ratio") double winRatio,
        String rank
) {
    public static UserStatsDTO fromAccount(Account account) {
        long totalWon = account.getTotalWon();
        String rank;

        if (totalWon <= 10_000) {
            rank = "Sucker";
        } else if (totalWon <= 50_000) {
            rank = "Fish";
        } else if (totalWon <= 250_000) {
            rank = "Grinder";
        } else if (totalWon <= 1_000_000) {
            rank = "Shark";
        } else if (totalWon <= 5_000_000) {
            rank = "Whale";
        } else {
            rank = "High Roller";
        }

        double ratio = 0.0;
        if (account.getHandsPlayed() > 0) {
            ratio = (double) account.getHandsWon() / account.getHandsPlayed() * 100.0;
            ratio = Math.round(ratio * 10.0) / 10.0;
        }

        return new UserStatsDTO(
                account.getHandsPlayed(),
                totalWon,
                account.getBiggestPot(),
                ratio,
                rank
        );
    }
}