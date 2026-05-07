package com.poker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final TableManager tableManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = (String) headerAccessor.getSessionAttributes().get("userId");

        if (userId != null) {
            log.info("User Disconnected (closed tab): " + userId);

            if (tableManager.isPlayerActive(userId)) {
                log.info("User {} is active. Scheduling auto-kick in 3 seconds...", userId);

                scheduler.schedule(() -> {
                    if (tableManager.isPlayerActive(userId)) {
                        tableManager.forceKickPlayer(userId);
                        log.info("Auto-kicked user {} after grace period", userId);
                    }
                }, 3, TimeUnit.SECONDS);
            }
        }
    }
}