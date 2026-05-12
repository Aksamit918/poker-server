package com.poker.model;

import java.util.*;
import java.util.stream.Collectors;

public class HandEvaluator {

    private static Map<Rank, Long> getRankCounts(List<Card> cards) {
        return cards.stream().collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
    }

    private static Map<Suit, Long> getSuitCounts(List<Card> cards) {
        return cards.stream().collect(Collectors.groupingBy(Card::getSuit, Collectors.counting()));
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

    private static List<Rank> getRanksWithAtLeast(Map<Rank, Long> counts, int target) {
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= (long)target)
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
            List<Card> flushCards = allCards.stream().filter(c -> c.getSuit() == flushSuit).toList();
            List<Rank> sequence = getSequenceOfFive(flushCards.stream().map(Card::getRank).toList());
            if (!sequence.isEmpty()) {
                List<Card> rankC = sequence.stream().map(r -> flushCards.stream().filter(c -> c.getRank() == r).findFirst().get()).toList();
                if (sequence.get(0) == Rank.ACE && sequence.get(1) == Rank.KING) {
                    return new HandResult(HandCategory.ROYAL_FLUSH, Collections.emptyList(), rankC, Collections.emptyList());
                }
                return new HandResult(HandCategory.STRAIGHT_FLUSH, List.of(sequence.get(0)), rankC, Collections.emptyList());
            }
        }

        List<Rank> quads = getRanksWithAtLeast(rankCounts, 4);
        if (!quads.isEmpty()) {
            Rank m = quads.get(0);
            List<Card> rankC = new ArrayList<>(allCards.stream().filter(c -> c.getRank() == m).limit(4).toList());
            List<Card> kickC = allCards.stream().filter(c -> c.getRank() != m).limit(1).toList();
            return new HandResult(HandCategory.FOUR_OF_A_KIND, List.of(m, kickC.get(0).getRank()), rankC, kickC);
        }

        List<Rank> trips = getRanksWithAtLeast(rankCounts, 3);
        List<Rank> pairs = getRanksWithAtLeast(rankCounts, 2);

        if (!trips.isEmpty()) {
            final Rank t = trips.get(0);
            Rank pCandidate = null;
            if (trips.size() >= 2) pCandidate = trips.get(1);
            else if (!pairs.isEmpty()) pCandidate = pairs.stream().filter(r -> r != t).findFirst().orElse(null);

            if (pCandidate != null) {
                final Rank p = pCandidate;
                List<Card> rankC = new ArrayList<>(allCards.stream().filter(c -> c.getRank() == t).limit(3).toList());
                rankC.addAll(allCards.stream().filter(c -> c.getRank() == p).limit(2).toList());
                return new HandResult(HandCategory.FULL_HOUSE, List.of(t, p), rankC, Collections.emptyList());
            }
        }

        if (flushSuit != null) {
            List<Card> rankC = allCards.stream().filter(c -> c.getSuit() == flushSuit).limit(5).toList();
            return new HandResult(HandCategory.FLUSH, rankC.stream().map(Card::getRank).toList(), rankC, Collections.emptyList());
        }

        List<Rank> strSeq = getSequenceOfFive(sortedUniqueRanks);
        if (!strSeq.isEmpty()) {
            List<Card> rankC = new ArrayList<>();
            for (Rank r : strSeq) rankC.add(allCards.stream().filter(c -> c.getRank() == r).findFirst().get());
            return new HandResult(HandCategory.STRAIGHT, List.of(strSeq.get(0)), rankC, Collections.emptyList());
        }

        if (!trips.isEmpty()) {
            Rank m = trips.get(0);
            List<Card> rankC = allCards.stream().filter(c -> c.getRank() == m).limit(3).toList();
            List<Card> kickC = allCards.stream().filter(c -> c.getRank() != m).limit(2).toList();
            List<Rank> tieB = new ArrayList<>();
            tieB.add(m);
            kickC.forEach(c -> tieB.add(c.getRank()));
            return new HandResult(HandCategory.THREE_OF_A_KIND, tieB, rankC, kickC);
        }

        if (pairs.size() >= 2) {
            final Rank p1 = pairs.get(0);
            final Rank p2 = pairs.get(1);

            List<Card> rankC = new ArrayList<>(allCards.stream().filter(c -> c.getRank() == p1).limit(2).toList());
            rankC.addAll(allCards.stream().filter(c -> c.getRank() == p2).limit(2).toList());

            Card kickerCard = allCards.stream()
                    .filter(c -> c.getRank() != p1 && c.getRank() != p2)
                    .findFirst().orElse(allCards.get(0));

            List<Card> kickC = List.of(kickerCard);
            return new HandResult(HandCategory.TWO_PAIRS, List.of(p1, p2, kickerCard.getRank()), rankC, kickC);
        }

        if (!pairs.isEmpty()) {
            final Rank p1 = pairs.get(0);
            List<Card> rankC = allCards.stream().filter(c -> c.getRank() == p1).limit(2).toList();
            List<Card> kickC = allCards.stream().filter(c -> c.getRank() != p1).limit(3).toList();

            List<Rank> tieB = new ArrayList<>();
            tieB.add(p1);
            kickC.forEach(c -> tieB.add(c.getRank()));
            return new HandResult(HandCategory.ONE_PAIR, tieB, rankC, kickC);
        }

        List<Card> rankC = List.of(allCards.get(0));
        List<Card> kickC = allCards.subList(1, 5);
        List<Rank> tieB = allCards.stream().limit(5).map(Card::getRank).collect(Collectors.toList());
        return new HandResult(HandCategory.HIGH_CARD, tieB, rankC, kickC);
    }
}