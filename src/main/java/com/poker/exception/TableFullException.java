package com.poker.exception;

public class TableFullException extends RuntimeException {
    public TableFullException(String message) {
        super(message);
    }
}
