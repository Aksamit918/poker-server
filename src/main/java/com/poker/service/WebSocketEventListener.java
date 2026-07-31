package com.poker.service;

import com.poker.dto.TableDetailsDTO;
import com.poker.model.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final TableManager tableManager;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String userId = (String) sessionAttributes.get("userId");
            if (userId != null) {
                log.info("WebSocket disconnect for user: {}. Scheduling grace period kick...", userId);
                tableManager.scheduleDisconnectKick(userId);
            }
        }
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String userId = (String) sessionAttributes.get("userId");
            String destination = headerAccessor.getDestination();

            if (userId != null && destination != null && destination.startsWith("/topic/table/")) {

                log.info("User {} subscribed to {}. Canceling grace period kick...", userId, destination);
                tableManager.cancelDisconnectTask(userId);

                String tableId = destination.substring("/topic/table/".length());

                Table table = tableManager.getTable(tableId);
                if (table != null) {
                    TableDetailsDTO snapshot = TableDetailsDTO.createTableDetailsDTO(table, userId);

                    messagingTemplate.convertAndSendToUser(
                            userId,
                            "/queue/table_snapshot",
                            snapshot
                    );
                    log.info("Sent TABLE_UPDATE snapshot to user {}", userId);
                }
            }
        }
    }
}