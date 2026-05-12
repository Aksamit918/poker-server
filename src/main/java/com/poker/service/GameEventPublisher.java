package com.poker.service;

import com.poker.dto.TableDetailsDTO;
import com.poker.dto.events.LobbyUpdateEvent;
import com.poker.dto.events.PlayerActionEvent;
import com.poker.dto.events.PlayerStatusEvent;
import com.poker.dto.events.WalletUpdateEvent;
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

    public void publishLobbyUpdate(String tableId, int currentPlayers, int maxPlayers) {
        LobbyUpdateEvent event = new LobbyUpdateEvent("LOBBY_UPDATE", tableId, currentPlayers, maxPlayers);
        redisTemplate.convertAndSend("poker:lobby", event);
    }

    public void publishWalletUpdate(String userId, long newBalance, String reason) {
        WalletUpdateEvent event = new WalletUpdateEvent(userId, newBalance, reason);
        redisTemplate.convertAndSend("poker:wallet:" + userId, event);
    }
}