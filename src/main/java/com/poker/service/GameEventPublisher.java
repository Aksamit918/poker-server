package com.poker.service;

import com.poker.dto.TableDTO;
import com.poker.dto.TableDetailsDTO;
import com.poker.dto.events.*;
import com.poker.util.RedisTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
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

    public void publishFullLobbyUpdate(List<TableDTO> tables) {
        Map<String, Object> payload = Map.of(
                "event_type", "LOBBY_UPDATE",
                "tables", tables
        );
        redisTemplate.convertAndSend("poker:lobby", payload);
    }

    public void publishLobbyUpdate(String tableId, int currentPlayers, int maxPlayers) {
        LobbyTableUpdateDTO formA = new LobbyTableUpdateDTO(
                "LOBBY_UPDATE",
                tableId,
                currentPlayers,
                maxPlayers
        );
        redisTemplate.convertAndSend("poker:lobby", formA);
    }

    public void publishWalletUpdate(String userId, long newBalance, String reason) {
        WalletUpdateEvent event = new WalletUpdateEvent(userId, newBalance, reason);
        redisTemplate.convertAndSend("poker:wallet:" + userId, event);
    }
}