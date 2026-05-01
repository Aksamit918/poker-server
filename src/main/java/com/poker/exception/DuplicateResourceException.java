package com.poker.exception;

public class DuplicateResourceException extends PokerException {
    public DuplicateResourceException(String key) {
        super(key);
    }
}
