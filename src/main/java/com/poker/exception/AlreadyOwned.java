package com.poker.exception;

public class AlreadyOwned extends PokerException {
    public AlreadyOwned(String key, Object... args) {
        super(key, args);
    }
}
