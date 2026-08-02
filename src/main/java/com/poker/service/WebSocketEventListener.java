package com.poker.service;

import com.poker.dto.TableDTO;
import com.poker.dto.events.TableDetailsDTO;
import com.poker.model.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final TableManager tableManager;
    private final SimpMessagingTemplate messagingTemplate;

    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    private final Map<String, String> activeUserSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String userId = (String) sessionAttributes.get("userId");
            String sessionId = headerAccessor.getSessionId();

            if (userId != null) {
                activeUserSessions.put(userId, sessionId);
                tableManager.cancelDisconnectTask(userId);

                boolean isNewUser = onlineUsers.add(userId);
                if (isNewUser) {
                    broadcastOnlineCount();
                }

                sendLobbySnapshotToUser(userId);
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String userId = (String) sessionAttributes.get("userId");
            String disconnectedSessionId = headerAccessor.getSessionId();

            if (userId != null) {
                String currentActiveSessionId = activeUserSessions.get(userId);

                if (currentActiveSessionId != null && !currentActiveSessionId.equals(disconnectedSessionId)) {
                    log.info("Ghost session {} disconnected for user {}. Ignoring because active session is {}.",
                            disconnectedSessionId, userId, currentActiveSessionId);
                    return;
                }

                log.info("Active WebSocket disconnect for user: {}. Scheduling grace period kick...", userId);
                activeUserSessions.remove(userId);
                tableManager.scheduleDisconnectKick(userId);

                onlineUsers.remove(userId);
                broadcastOnlineCount();
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

            if (userId != null && destination != null && destination.equals("/topic/lobby")) {
                broadcastOnlineCount();
                sendLobbySnapshotToUser(userId);
            }

            if (userId != null && destination != null && destination.startsWith("/topic/table/")) {
                log.info("User {} subscribed to {}. Canceling grace period kick...", userId, destination);
                tableManager.cancelDisconnectTask(userId);

                String tableId = destination.substring("/topic/table/".length());

                Table table = tableManager.getTable(tableId);
                if (table != null) {
                    try {
                        TableDetailsDTO snapshot = TableDetailsDTO.createTableDetailsDTO(table, userId, true);
                        messagingTemplate.convertAndSendToUser(
                                userId,
                                "/queue/table_snapshot",
                                snapshot
                        );
                        log.info("Sent TABLE_UPDATE snapshot to user {}", userId);
                    } catch (Exception e) {
                        log.error("Failed to build or send snapshot for table {} to user {}", tableId, userId, e);
                    }
                }
            }
        }
    }

    private void sendLobbySnapshotToUser(String userId) {
        List<TableDTO> currentLobby = tableManager.getAllTables().stream()
                .map(TableDTO::createTableDTO)
                .toList();

        Map<String, Object> lobbySnapshot = Map.of(
                "event_type", "LOBBY_UPDATE",
                "tables", currentLobby
        );

        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/lobby_snapshot",
                lobbySnapshot
        );
    }

    private void broadcastOnlineCount() {
        Map<String, Object> payload = Map.of(
                "event_type", "ONLINE_UPDATE",
                "online_count", onlineUsers.size()
        );
        messagingTemplate.convertAndSend("/topic/lobby", payload);
    }
}