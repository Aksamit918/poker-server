package com.poker.exception;

public class InvalidInputException extends PokerException {
    public InvalidInputException(String key, Object ... args) {
        super(key, args);
    }
}
