package com.poker.model;

import java.util.List;

public class HandResult implements Comparable<HandResult> {
    private final HandCategory category;
    private final List<Rank> tieBreakers;
    private final List<Card> winningCards;

    public HandResult(HandCategory category, List<Rank> tieBreakers, List<Card> winningCards) {
        this.category = category;
        this.tieBreakers = tieBreakers;
        this.winningCards = winningCards;
    }

    public HandCategory getCategory() { return category; }
    public List<Card> getWinningCards() { return winningCards; }

    @Override
    public int compareTo(HandResult other) {
        int categoryCompare = category.compareTo(other.category);
        if (categoryCompare != 0) return categoryCompare;
        for (int i = 0; i < this.tieBreakers.size(); i++) {
            int rankCompare = Integer.compare(this.tieBreakers.get(i).getWeight(), other.tieBreakers.get(i).getWeight());
            if (rankCompare != 0) return rankCompare;
        }
        return 0;
    }
}
