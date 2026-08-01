package com.poker.controller;

import com.poker.dto.events.EmotePayloadDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class EmoteController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/table/{tableId}/emote")
    public void handleEmote(@DestinationVariable String tableId, EmotePayloadDTO payload) {

        log.info("User {} sent emote {} to table {}", payload.userId(), payload.emoteId(), tableId);

        String destination = "/topic/table/" + tableId;
        messagingTemplate.convertAndSend(destination, payload);
    }
}