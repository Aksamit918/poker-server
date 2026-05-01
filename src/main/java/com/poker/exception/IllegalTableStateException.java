package com.poker.exception;

public class IllegalTableStateException extends PokerException {
    public IllegalTableStateException(String key) {
        super(key);
    }
}
