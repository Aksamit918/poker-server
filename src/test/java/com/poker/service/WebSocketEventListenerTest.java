package com.poker.service;

import com.poker.config.StompAuthChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    TableManager tableManager;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    MessageChannel channel;

    WebSocketEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new WebSocketEventListener(tableManager, messagingTemplate);
    }

    @Test
    void lobbySubscribeSendsSnapshotOnlyAfterBrokerHandler() {
        when(tableManager.getAllTables()).thenReturn(List.of());
        Message<byte[]> message = subscribeMessage("/topic/lobby");

        listener.afterMessageHandled(message, channel, mock(MessageHandler.class), null);
        verifyNoInteractions(messagingTemplate);

        listener.afterMessageHandled(message, channel, mock(SimpleBrokerMessageHandler.class), null);

        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("42"),
                eq("/queue/lobby_snapshot"),
                any()
        );
    }

    @Test
    void connectIsRegisteredFromPreSendOnly() {
        listener.preSend(connectMessage(), channel);

        verify(tableManager).cancelDisconnectTask("42");
        verify(messagingTemplate).convertAndSend(eq("/topic/lobby"), any(Object.class));
    }

    private static Message<byte[]> subscribeMessage(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setSessionId("sess-1");
        accessor.setDestination(destination);
        accessor.setSessionAttributes(new ConcurrentHashMap<>(Map.of(
                StompAuthChannelInterceptor.USER_ID_ATTRIBUTE, "42"
        )));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> connectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionId("sess-1");
        accessor.setSessionAttributes(new ConcurrentHashMap<>(Map.of(
                StompAuthChannelInterceptor.USER_ID_ATTRIBUTE, "42"
        )));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
