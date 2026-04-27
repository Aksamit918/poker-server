package com.poker.service;

import com.poker.dto.TableDetailsDTO;
import com.poker.dto.events.PlayerActionEvent;
import com.poker.dto.events.PlayerStatusEvent;
import com.poker.util.RedisTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    public void publishTableUpdate(TableDetailsDTO tableDetails) {
        String topic = RedisTopics.getTableTopic(tableDetails.tableId());
        redisTemplate.convertAndSend(topic, tableDetails);
    }

    public void publishPlayerAction(PlayerActionEvent event) {
        String topic = RedisTopics.getTableTopic(event.tableId());
        redisTemplate.convertAndSend(topic, event);
    }

    public void publishPlayerStatus(PlayerStatusEvent event) {
        String topic = RedisTopics.getTableTopic(event.tableId());
        redisTemplate.convertAndSend(topic, event);
    }
}