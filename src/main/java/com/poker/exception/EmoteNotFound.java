package com.poker.exception;

public class EmoteNotFound extends PokerException {
    public EmoteNotFound(String key, Object... args) {
        super(key, args);
    }
}
