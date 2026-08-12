package com.poker.exception;

public class InsufficientFunds extends PokerException {
    public InsufficientFunds(String key, Object... args) {
        super(key, args);
    }
}
