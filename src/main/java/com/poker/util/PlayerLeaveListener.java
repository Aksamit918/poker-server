package com.poker.util;

@FunctionalInterface
public interface PlayerLeaveListener {
    void onPlayerLeave(String userId, long finalChips);
}
