package com.poker.service;

import com.poker.dto.OnlineUpdateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineCounterService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    @Scheduled(fixedRate = 10000)
    public void broadcastOnlineCount() {
        int onlineCount = userRegistry.getUserCount();

        OnlineUpdateDTO payload = new OnlineUpdateDTO("ONLINE_UPDATE", onlineCount);

        messagingTemplate.convertAndSend("/topic/lobby", payload);
    }
}