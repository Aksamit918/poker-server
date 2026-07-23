package com.poker.controller;

import com.poker.dto.PingPayloadDTO;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class PingController {

    @MessageMapping("/ping")
    @SendToUser("/queue/pong")
    public PingPayloadDTO handlePing(PingPayloadDTO payload) {
        return payload;
    }
}