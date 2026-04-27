package com.poker.util;

public class RedisTopics {
    private static final String TOPIC_PREFIX = "poker:table:";

    public static String getTableTopic(String tableId) {
        return TOPIC_PREFIX + tableId;
    }
}