package com.assu.server.domain.certification.config;

import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.chat.repository.ChatRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.global.util.PrincipalDetails;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private static final Pattern CHAT_ROOM = Pattern.compile("/sub/chat/(\\d{1,18})");
	private static final Pattern CHAT_LIST = Pattern.compile("/sub/chat/list/(\\d{1,18})");
	private static final Pattern CERTIFICATION_PROGRESS = Pattern.compile("/certification/progress/\\d{1,18}");
	private static final String USER_ERROR_QUEUE = "/user/queue/errors";

	private final JwtUtil jwtUtil;
	private final ChatRepository chatRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}
		if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
			authorizeSubscribe(accessor);
			return message;
		}
		if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
			return message;
		}

		String authHeader = accessor.getFirstNativeHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			log.warn("[STOMP CONNECT] Authorization 헤더 없음 또는 형식 불일치");
			throw new GeneralException(ErrorStatus.JWT_TOKEN_NOT_RECEIVED);
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

	private void authorizeSubscribe(StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		if (!(accessor.getUser() instanceof Authentication authentication)
				|| !(authentication.getPrincipal() instanceof PrincipalDetails principal)) {
			log.warn("[STOMP SUBSCRIBE] 인증 정보 없는 구독 거부 - destination={}", destination);
			throw new GeneralException(ErrorStatus._UNAUTHORIZED);
		}
		if (!canSubscribe(principal, destination)) {
			log.warn("[STOMP SUBSCRIBE] 권한 없는 구독 거부 - memberId={}, destination={}", principal.getId(), destination);
			throw new GeneralException(ErrorStatus._FORBIDDEN);
		}
	}

	// 브로커가 구독 경로를 Ant 패턴으로 해석하므로 숫자 id로 끝나는 정확한 경로만 허용한다
	private boolean canSubscribe(PrincipalDetails principal, String destination) {
		if (destination == null) {
			return false;
		}
		Matcher chatList = CHAT_LIST.matcher(destination);
		if (chatList.matches()) {
			return principal.getId().equals(Long.valueOf(chatList.group(1)));
		}
		Matcher chatRoom = CHAT_ROOM.matcher(destination);
		if (chatRoom.matches()) {
			return chatRepository.existsByIdAndParticipantId(Long.valueOf(chatRoom.group(1)), principal.getId());
		}
		if (CERTIFICATION_PROGRESS.matcher(destination).matches()) {
			return principal.getRole() == UserRole.STUDENT;
		}
		return USER_ERROR_QUEUE.equals(destination);
	}
}
