package com.poker.exception;

public class NotYourTurnException extends PokerException {
    public NotYourTurnException(String key) {
        super(key);
    }
}
