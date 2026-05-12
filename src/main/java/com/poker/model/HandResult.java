package com.poker.model;

import java.util.List;

public class HandResult implements Comparable<HandResult> {
    private final HandCategory category;
    private final List<Rank> tieBreakers;
    private final List<Card> rankCards;   // Карты, образующие ранг
    private final List<Card> kickerCards; // Вспомогательные карты (кикеры)

    public HandResult(HandCategory category, List<Rank> tieBreakers, List<Card> rankCards, List<Card> kickerCards) {
        this.category = category;
        this.tieBreakers = tieBreakers;
        this.rankCards = rankCards;
        this.kickerCards = kickerCards;
    }

    public HandCategory getCategory() { return category; }
    public List<Card> getRankCards() { return rankCards; }
    public List<Card> getKickerCards() { return kickerCards; }
    public List<Rank> getTieBreakers() { return tieBreakers; }

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
