package com.assu.server.domain.certification.config;

import com.assu.server.domain.auth.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
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
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

		if (StompCommand.CONNECT.equals(accessor.getCommand())) {
			String authHeader = accessor.getFirstNativeHeader("Authorization");

			if (authHeader != null && authHeader.startsWith("Bearer ")) {
				String token = jwtUtil.getTokenFromHeader(authHeader);
				Authentication authentication = jwtUtil.getAuthentication(token);

				// ⭐️ 이 부분을 수정
				accessor.setUser(authentication);

				// ⭐️ 추가: 메시지 헤더에도 Authentication 정보 저장
				accessor.setHeader(StompHeaderAccessor.USER_HEADER, authentication);

				log.info("Authentication set: {}", authentication);
			}
		}
		return message;
	}
}