package com.poker.exception;

public class IllegalRaiseException extends PokerException {
    public IllegalRaiseException(String key, Object ... args) {
        super(key, args);
    }
}
