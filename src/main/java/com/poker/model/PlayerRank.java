package com.poker.model;

public enum PlayerRank {
    SUCKER("Sucker", 0L),
    FISH("Fish", 5_000L),
    DONK("Donk", 25_000L),
    LUCKY("Lucky", 100_000L),
    GRINDER("Grinder", 500_000L),
    SHARK("Shark", 2_000_000L),
    WHALE("Whale", 10_000_000L),
    LEGEND("Legend", 50_000_000L);

    private final String displayName;
    private final long minTotalWon;

    PlayerRank(String displayName, long minTotalWon) {
        this.displayName = displayName;
        this.minTotalWon = minTotalWon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getMinTotalWon() {
        return minTotalWon;
    }

    public PlayerRank next() {
        int nextOrdinal = ordinal() + 1;
        if (nextOrdinal >= values().length) {
            return null;
        }
        return values()[nextOrdinal];
    }

    public static PlayerRank fromTotalWon(long totalWon) {
        PlayerRank[] ranks = values();
        PlayerRank current = ranks[0];
        for (PlayerRank rank : ranks) {
            if (totalWon >= rank.minTotalWon) {
                current = rank;
            } else {
                break;
            }
        }
        return current;
    }
}
