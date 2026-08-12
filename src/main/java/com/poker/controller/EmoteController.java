package com.poker.controller;

import com.poker.dto.events.EmotePayloadDTO;
import com.poker.service.EmoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class EmoteController {

    private final SimpMessagingTemplate messagingTemplate;
    private final EmoteService emoteService;

    @MessageMapping("/table/{tableId}/emote")
    public void handleEmote(
            @DestinationVariable String tableId,
            EmotePayloadDTO payload,
            Principal principal
    ) {
        if (principal == null || principal.getName() == null) {
            log.warn("Rejected emote to table {}: missing principal", tableId);
            return;
        }

        String authUserId = principal.getName().trim();
        Long userId;
        try {
            userId = Long.parseLong(authUserId);
        } catch (NumberFormatException e) {
            log.warn("Rejected emote to table {}: invalid principal '{}'", tableId, authUserId);
            return;
        }

        if (!emoteService.canSendEmote(userId, payload.emoteId())) {
            log.warn("Rejected emote '{}' from user {} to table {}: not owned or unknown",
                    payload.emoteId(), authUserId, tableId);
            return;
        }

        EmotePayloadDTO safePayload = new EmotePayloadDTO(
                payload.eventType() != null ? payload.eventType() : "EMOTE",
                authUserId,
                payload.emoteId()
        );

        log.info("User {} sent emote {} to table {}", authUserId, safePayload.emoteId(), tableId);
        messagingTemplate.convertAndSend("/topic/table/" + tableId, safePayload);
    }
}
