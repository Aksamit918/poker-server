package com.poker.exception;

public class TableFullException extends PokerException {
    public TableFullException(String key) {
        super(key);
    }
}
