package com.assu.server.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.assu.server.domain.certification.config.StompAuthChannelInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        registry.addEndpoint("/ws")  // 클라이언트 WebSocket 연결 지점
                .setAllowedOriginPatterns(
                        "*",
                        "https://assu.shop",
                        "http://localhost:63342",
                        "http://localhost:5173",     // Vite 기본
                        "http://localhost:3000",     // CRA/Next 기본
                        "http://127.0.0.1:*",
                        "http://192.168.*.*:*");
        // 채팅용 엔드포인트

        // 인증용 엔드포인트
        registry.addEndpoint("/ws-certify")
            .setAllowedOriginPatterns("*");


    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 채팅용 & 인증용 합치기
        registry.enableSimpleBroker("/sub","/queue", "/certification");
        registry.setApplicationDestinationPrefixes("/pub", "/app"); // 둘 다 추가
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}