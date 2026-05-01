package com.poker.exception;

public class GameInProgressException extends PokerException {
    public GameInProgressException(String key) {
        super(key);
    }
}
