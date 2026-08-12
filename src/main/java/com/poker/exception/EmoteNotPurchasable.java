package com.poker.exception;

public class EmoteNotPurchasable extends PokerException {
    public EmoteNotPurchasable(String key, Object... args) {
        super(key, args);
    }
}
