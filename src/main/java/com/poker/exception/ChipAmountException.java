package com.poker.exception;

public class ChipAmountException extends PokerException {
    public ChipAmountException(String key, Object ... args) {
        super(key, args);
    }
}
