package com.poker.model;

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
        allCards.sort(Comparator.comparing(Card::getRank).reversed());

        Map<Suit, Long> suitCounts = getSuitCounts(allCards);
        Map<Rank, Long> rankCounts = getRankCounts(allCards);
        List<Rank> sortedUniqueRanks = rankCounts.keySet().stream().sorted(Comparator.reverseOrder()).toList();

        Suit flushSuit = suitCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 5).map(Map.Entry::getKey).findFirst().orElse(null);

        if (flushSuit != null) {
            List<Card> suitCards = allCards.stream().filter(c -> c.getSuit() == flushSuit).toList();
            List<Rank> sequence = getSequenceOfFive(suitCards.stream().map(Card::getRank).toList());
            if (!sequence.isEmpty()) {
                List<Card> winCards = sequence.stream().map(r -> suitCards.stream().filter(c -> c.getRank() == r).findFirst().get()).toList();
                if (sequence.get(0) == Rank.ACE && sequence.get(1) == Rank.KING)
                    return new HandResult(HandCategory.ROYAL_FLUSH, Collections.emptyList(), winCards);
                return new HandResult(HandCategory.STRAIGHT_FLUSH, List.of(sequence.get(0)), winCards);
            }
        }

        List<Rank> quads = getRanksWithCount(rankCounts, 4);
        if (!quads.isEmpty()) {
            Rank m = quads.get(0);
            List<Card> win = new ArrayList<>(allCards.stream().filter(c -> c.getRank() == m).toList());
            win.add(allCards.stream().filter(c -> c.getRank() != m).findFirst().get());
            return new HandResult(HandCategory.FOUR_OF_A_KIND, List.of(m), win);
        }

        List<Rank> trips = getRanksWithCount(rankCounts, 3);
        List<Rank> pairs = getRanksWithCount(rankCounts, 2);
        if (trips.size() >= 2 || (trips.size() == 1 && !pairs.isEmpty())) {
            Rank t = trips.get(0);
            Rank p = (trips.size() >= 2) ? trips.get(1) : pairs.get(0);
            List<Card> win = new ArrayList<>(allCards.stream().filter(c -> c.getRank() == t).toList());
            win.addAll(allCards.stream().filter(c -> c.getRank() == p).limit(2).toList());
            return new HandResult(HandCategory.FULL_HOUSE, List.of(t, p), win);
        }

        if (flushSuit != null) {
            List<Card> win = allCards.stream().filter(c -> c.getSuit() == flushSuit).limit(5).toList();
            return new HandResult(HandCategory.FLUSH, win.stream().map(Card::getRank).toList(), win);
        }

        List<Rank> strSeq = getSequenceOfFive(sortedUniqueRanks);
        if (!strSeq.isEmpty()) {
            List<Card> win = strSeq.stream().map(r -> allCards.stream().filter(c -> c.getRank() == r).findFirst().get()).toList();
            return new HandResult(HandCategory.STRAIGHT, List.of(strSeq.get(0)), win);
        }

        if (!trips.isEmpty()) {
            Rank m = trips.get(0);
            List<Card> win = new ArrayList<>(allCards.stream().filter(c -> c.getRank() == m).toList());
            win.addAll(allCards.stream().filter(c -> c.getRank() != m).limit(2).toList());
            return new HandResult(HandCategory.THREE_OF_A_KIND, List.of(m), win);
        }

        if (pairs.size() >= 2) {
            Rank p1 = pairs.get(0); Rank p2 = pairs.get(1);
            List<Card> win = new ArrayList<>(allCards.stream().filter(c -> c.getRank() == p1).toList());
            win.addAll(allCards.stream().filter(c -> c.getRank() == p2).toList());
            win.add(allCards.stream().filter(c -> c.getRank() != p1 && c.getRank() != p2).findFirst().get());
            return new HandResult(HandCategory.TWO_PAIRS, List.of(p1, p2), win);
        }

        if (pairs.size() == 1) {
            Rank p1 = pairs.get(0);
            List<Card> win = new ArrayList<>(allCards.stream().filter(c -> c.getRank() == p1).toList());
            win.addAll(allCards.stream().filter(c -> c.getRank() != p1).limit(3).toList());
            return new HandResult(HandCategory.ONE_PAIR, List.of(p1), win);
        }

        List<Card> win = allCards.stream().limit(5).toList();
        return new HandResult(HandCategory.HIGH_CARD, win.stream().map(Card::getRank).toList(), win);
    }
}