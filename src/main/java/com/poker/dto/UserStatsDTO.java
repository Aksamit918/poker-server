package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.PlayerRank;
import com.poker.persistence.entity.Account;

public record UserStatsDTO(
        @JsonProperty("hands_played") int handsPlayed,
        @JsonProperty("total_won") long totalWon,
        @JsonProperty("biggest_pot") long biggestPot,
        @JsonProperty("win_ratio") double winRatio,
        String rank,
        @JsonProperty("rank_progress_percent") double rankProgressPercent,
        @JsonProperty("next_rank") String nextRank,
        @JsonProperty("chips_to_next_rank") long chipsToNextRank,
        @JsonProperty("rank_min_total_won") long rankMinTotalWon,
        @JsonProperty("rank_max_total_won") Long rankMaxTotalWon
) {
    public static UserStatsDTO fromAccount(Account account) {
        long totalWon = account.getTotalWon();
        PlayerRank current = PlayerRank.fromTotalWon(totalWon);
        PlayerRank next = current.next();

        long rankMin = current.getMinTotalWon();
        Long rankMax = next != null ? next.getMinTotalWon() : null;
        String nextRankName = next != null ? next.getDisplayName() : null;

        double progressPercent;
        long chipsToNext;
        if (next == null || rankMax == null) {
            progressPercent = 100.0;
            chipsToNext = 0L;
        } else {
            long span = rankMax - rankMin;
            long progressed = Math.max(0L, totalWon - rankMin);
            progressPercent = span <= 0 ? 100.0 : (progressed * 100.0) / span;
            if (progressPercent < 0) progressPercent = 0;
            if (progressPercent > 100) progressPercent = 100;
            progressPercent = Math.round(progressPercent * 10.0) / 10.0;
            chipsToNext = Math.max(0L, rankMax - totalWon);
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
                current.getDisplayName(),
                progressPercent,
                nextRankName,
                chipsToNext,
                rankMin,
                rankMax
        );
    }
}
