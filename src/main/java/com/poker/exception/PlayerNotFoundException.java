package com.poker.exception;

public class PlayerNotFoundException extends PokerException {
    public PlayerNotFoundException(String key) {
        super(key);
    }
}
