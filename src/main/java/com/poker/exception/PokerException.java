package com.poker.exception;

public abstract class PokerException extends RuntimeException {
    private final Object[] args;

    public PokerException(String messageKey, Object... args) {
        super(messageKey);
        this.args = args;
    }

    public Object[] getArgs() {
        return args;
    }
}