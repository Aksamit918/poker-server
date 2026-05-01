package com.poker.exception;

public class AccountNotFoundException extends PokerException {
    public AccountNotFoundException(String key) {
        super(key);
    }
}
