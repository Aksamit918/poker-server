package com.poker.controller;

import com.poker.dto.EmotePayloadDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class EmoteController {
    @MessageMapping("/table/{tableId}/emote")
    @SendTo("/topic/table/{tableId}")
    public EmotePayloadDTO handleEmote(@DestinationVariable String tableId, EmotePayloadDTO payload) {
        log.info("User {} sent emote {} to table {}", payload.userId(), payload.emoteId(), tableId);
        return payload;
    }
}
