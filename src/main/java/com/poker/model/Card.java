package com.poker.model;

public class Card {
    private final Rank rank;
    private final Suit suit;
    public Card(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }
    public Rank getRank() {
        return rank;
    }
    public Suit getSuit() {
        return suit;
    }
    public String getShortName() {
        String r = switch (rank) {
            case ACE -> "A";
            case KING -> "K";
            case QUEEN -> "Q";
            case JACK -> "J";
            default -> String.valueOf(rank.getWeight());
        };
        String s = suit.name().substring(0, 1);
        return r + s;
    }
    @Override
    public String toString() {
        return rank + " of " + suit;
    }
}
