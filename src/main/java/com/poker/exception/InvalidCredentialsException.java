package com.poker.exception;

public class InvalidCredentialsException extends PokerException {
    public InvalidCredentialsException(String key) {
        super(key);
    }
}
