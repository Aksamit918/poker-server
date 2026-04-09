package com.poker.util;

import com.poker.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class HandEvaluator {

    private static Map<Rank, Long> getRankCounts(List<Card> cards) {
        return cards.stream()
                .collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
    }

    private static Map<Suit, Long> getSuitCounts(List<Card> cards) {
        return cards.stream()
                .collect(Collectors.groupingBy(Card::getSuit, Collectors.counting()));
    }

    private static List<Rank> getSequenceOfFive(List<Rank> sortedRanks) {
        if (sortedRanks.size() < 5) return Collections.emptyList();

        for (int i = 0; i <= sortedRanks.size() - 5; i++) {
            if (sortedRanks.get(i).getWeight() - sortedRanks.get(i + 4).getWeight() == 4) {
                return sortedRanks.subList(i, i + 5);
            }
        }

        if (sortedRanks.containsAll(List.of(Rank.ACE, Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO))) {
            return List.of(Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO, Rank.ACE);
        }

        return Collections.emptyList();
    }

    private static List<Rank> getRanksWithCount(Map<Rank, Long> counts, int target) {
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= target)
                .map(Map.Entry::getKey)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public static HandResult evaluate(List<Card> hand, List<Card> communityCards) {
        List<Card> allCards = new ArrayList<>(hand);
        allCards.addAll(communityCards);

        Map<Suit, Long> suitCounts = getSuitCounts(allCards);
        Map<Rank, Long> rankCounts = getRankCounts(allCards);
        List<Rank> sortedUniqueRanks = rankCounts.keySet().stream().sorted(Comparator.reverseOrder()).toList();

        Suit flushSuit = suitCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 5)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (flushSuit != null) {
            List<Rank> flushRanks = allCards.stream()
                    .filter(c -> c.getSuit() == flushSuit)
                    .map(Card::getRank)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList();

            List<Rank> strFlushSeq = getSequenceOfFive(flushRanks);
            if (!strFlushSeq.isEmpty()) {
                if (strFlushSeq.get(0) == Rank.ACE && strFlushSeq.get(1) == Rank.KING) {
                    return new HandResult(HandCategory.ROYAL_FLUSH, Collections.emptyList());
                }
                return new HandResult(HandCategory.STRAIGHT_FLUSH, List.of(strFlushSeq.get(0)));
            }
        }

        List<Rank> quads = getRanksWithCount(rankCounts, 4);
        if (!quads.isEmpty()) {
            Rank main = quads.get(0);
            Rank kicker = sortedUniqueRanks.stream().filter(r -> r != main).findFirst().orElse(main);
            return new HandResult(HandCategory.FOUR_OF_A_KIND, List.of(main, kicker));
        }

        List<Rank> trips = getRanksWithCount(rankCounts, 3);
        List<Rank> pairs = getRanksWithCount(rankCounts, 2);
        if (trips.size() >= 2) {
            return new HandResult(HandCategory.FULL_HOUSE, List.of(trips.get(0), trips.get(1)));
        } else if (trips.size() == 1 && !pairs.isEmpty()) {
            return new HandResult(HandCategory.FULL_HOUSE, List.of(trips.get(0), pairs.get(0)));
        }

        if (flushSuit != null) {
            List<Rank> tieBreakers = allCards.stream()
                    .filter(c -> c.getSuit() == flushSuit)
                    .map(Card::getRank)
                    .sorted(Comparator.reverseOrder())
                    .limit(5)
                    .toList();
            return new HandResult(HandCategory.FLUSH, tieBreakers);
        }

        List<Rank> strSeq = getSequenceOfFive(sortedUniqueRanks);
        if (!strSeq.isEmpty()) {
            return new HandResult(HandCategory.STRAIGHT, List.of(strSeq.get(0)));
        }

        if (!trips.isEmpty()) {
            Rank main = trips.get(0);
            List<Rank> tieBreakers = new ArrayList<>();
            tieBreakers.add(main);
            tieBreakers.addAll(sortedUniqueRanks.stream().filter(r -> r != main).limit(2).toList());
            return new HandResult(HandCategory.THREE_OF_A_KIND, tieBreakers);
        }

        if (pairs.size() >= 2) {
            Rank p1 = pairs.get(0);
            Rank p2 = pairs.get(1);
            Rank kicker = sortedUniqueRanks.stream().filter(r -> r != p1 && r != p2).findFirst().orElse(p1);
            return new HandResult(HandCategory.TWO_PAIRS, List.of(p1, p2, kicker));
        }

        if (pairs.size() == 1) {
            Rank p1 = pairs.get(0);
            List<Rank> tieBreakers = new ArrayList<>();
            tieBreakers.add(p1);
            tieBreakers.addAll(sortedUniqueRanks.stream().filter(r -> r != p1).limit(3).toList());
            return new HandResult(HandCategory.ONE_PAIR, tieBreakers);
        }

        return new HandResult(HandCategory.HIGH_CARD, sortedUniqueRanks.stream().limit(5).toList());
    }
}