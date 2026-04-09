package com.poker.exception;

public class IllegalTableStateException extends RuntimeException {
    public IllegalTableStateException(String message) {
        super(message);
    }
}
