package com.poker.config;

import com.poker.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AccountService accountService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-thread-");
        taskScheduler.initialize();

        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{20000, 20000})
                .setTaskScheduler(taskScheduler);

        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-poker")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authHeaders = accessor.getNativeHeader("Authorization");

                    if (authHeaders != null && !authHeaders.isEmpty()) {
                        String rawHeader = authHeaders.get(0);
                        String token = null;

                        if (rawHeader.startsWith("Bearer ")) {
                            token = rawHeader.substring(7);
                        }

                        if (token != null && !token.isBlank() && token.contains(".")) {
                            try {
                                String userId = accountService.getUserIdByToken(token);
                                if (userId != null) {
                                    accessor.getSessionAttributes().put("userId", userId);
                                    accessor.getSessionAttributes().put("jwtToken", token);
                                    accessor.setUser(() -> userId);
                                }
                            } catch (Exception e) {
                                log.warn("WS Connect rejected: invalid token");
                                throw new IllegalArgumentException("Invalid token");
                            }
                        }
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String token = (String) accessor.getSessionAttributes().get("jwtToken");

                    if (token == null) {
                        log.warn("WS Subscribe rejected: No token in session");
                        throw new IllegalArgumentException("Unauthorized");
                    }

                    try {
                        String userId = accountService.getUserIdByToken(token);
                        if (userId == null) {
                            log.warn("WS Subscribe rejected: Token expired during active session");
                            throw new IllegalArgumentException("Token expired");
                        }
                    } catch (Exception e) {
                        log.warn("WS Subscribe rejected: Token validation failed");
                        throw new IllegalArgumentException("Token expired");
                    }
                }

                return message;
            }
        });
    }
}