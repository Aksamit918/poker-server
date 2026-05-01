package com.poker.exception;

public class IllegalCheckException extends PokerException {
    public IllegalCheckException(String key, Object ... args) {
        super(key, args);
    }
}
