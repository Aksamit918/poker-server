package com.poker.model;

import com.poker.exception.EmptyDeckException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private List<Card> cards;
    public Deck() {
        cards = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        shuffle();
    }
    public void shuffle() {
        Collections.shuffle(cards);
    }
    public Card drawCard() {
        if (cards.isEmpty()) {
            throw new EmptyDeckException("Empty deck");
        }
        return cards.remove(0);
    }
}
