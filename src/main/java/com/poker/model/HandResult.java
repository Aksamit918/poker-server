package com.poker.model;

import java.util.List;

public class HandResult implements Comparable<HandResult> {
    HandCategory category;
    List<Rank> tieBreakers;
    public HandResult(HandCategory category, List<Rank> tieBreakers) {
        this.category = category;
        this.tieBreakers = tieBreakers;
    }
    @Override
    public int compareTo(HandResult other) {
        int categoryCompare = category.compareTo(other.category);

        if (categoryCompare != 0) {
            return categoryCompare;
        }

        for (int i = 0; i < this.tieBreakers.size(); i++) {
            int rankCompare = Integer.compare(
                    this.tieBreakers.get(i).getWeight(),
                    other.tieBreakers.get(i).getWeight()
            );

            if (rankCompare != 0) {
                return rankCompare;
            }
        }

        return 0;
    }
    @Override
    public String toString() {
        return category.toString() + " " + tieBreakers.toString();
    }
}
