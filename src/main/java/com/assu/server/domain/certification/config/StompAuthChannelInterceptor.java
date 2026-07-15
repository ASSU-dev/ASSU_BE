package com.assu.server.domain.certification.config;

import com.assu.server.domain.auth.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j; // SLF4j 로그 추가

@Slf4j // SLF4j 어노테이션 추가
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private final JwtUtil jwtUtil;
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) return message;

		if (StompCommand.CONNECT.equals(accessor.getCommand())) {
			String authHeader = accessor.getFirstNativeHeader("Authorization");
			log.info("[STOMP CONNECT] Authorization header: {}", authHeader != null ? "present" : "missing");

			if (authHeader != null && authHeader.startsWith("Bearer ")) {
				String token = jwtUtil.getTokenFromHeader(authHeader);
				try {
					Authentication authentication = jwtUtil.getAuthentication(token);
					accessor.setUser(authentication);
					log.info("[STOMP CONNECT] Authentication set: {}", authentication.getName());
				} catch (Exception e) {
					log.error("[STOMP CONNECT] JWT 인증 실패 - {}: {}", e.getClass().getSimpleName(), e.getMessage());
					throw e;
				}
			} else {
				log.warn("[STOMP CONNECT] Authorization 헤더 없음 또는 형식 불일치");
			}
		}
		return message;
	}
}