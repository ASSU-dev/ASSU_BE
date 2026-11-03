package com.assu.server.domain.certification.config;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private final JwtUtil jwtUtil;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
			return message;
		}

		String authHeader = accessor.getFirstNativeHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			log.warn("[STOMP CONNECT] Authorization 헤더 없음 또는 형식 불일치");
			throw new CustomAuthException(ErrorStatus.JWT_TOKEN_NOT_RECEIVED);
		}

		String token = jwtUtil.getTokenFromHeader(authHeader);
		try {
			// HTTP JwtAuthFilter와 같은 검사를 해야 탈퇴·로그아웃된 토큰이 웹소켓으로 우회하지 못한다
			jwtUtil.assertNotBlacklisted(token);
			Claims claims = jwtUtil.validateToken(token);
			jwtUtil.assertNotRevoked(claims);
			jwtUtil.assertAudience(claims, JwtUtil.AUD_APP);

			Authentication authentication = jwtUtil.getAuthentication(token);
			accessor.setUser(authentication);
			return message;
		} catch (RuntimeException e) {
			log.warn("[STOMP CONNECT] JWT 인증 실패 - {}: {}", e.getClass().getSimpleName(), e.getMessage());
			throw e;
		}
	}
}
