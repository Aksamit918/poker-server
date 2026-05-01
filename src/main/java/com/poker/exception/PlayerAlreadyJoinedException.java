package com.poker.exception;

public class PlayerAlreadyJoinedException extends PokerException {
    public PlayerAlreadyJoinedException(String key, Object ... args) {
        super(key, args);
    }
}
