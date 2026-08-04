package com.assu.server.domain.auth.service;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.auth.security.jwt.JwtUtil;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class LogoutServiceImplTest {

	@InjectMocks
	private LogoutServiceImpl logoutService;

	@Mock
	private JwtUtil jwtUtil;

	@Test
	@DisplayName("로그아웃 시 Access 토큰을 블랙리스트에 등록하고 해당 회원의 모든 Refresh 토큰을 제거한다")
	void logout_BlacklistsAccessAndRemovesAllRefreshTokens() {
		// 1. Given
		String authorization = "Bearer access-token";
		String rawAccessToken = "access-token";
		Long memberId = 7L;

		Claims claims = mock(Claims.class);
		when(claims.get("userId")).thenReturn(memberId.intValue());
		when(jwtUtil.getTokenFromHeader(authorization)).thenReturn(rawAccessToken);
		when(jwtUtil.validateTokenOnlySignature(rawAccessToken)).thenReturn(claims);

		// 2. When
		logoutService.logout(authorization);

		// 3. Then
		verify(jwtUtil, times(1)).blacklistAccess(rawAccessToken);
		verify(jwtUtil, times(1)).removeAllRefreshTokens(memberId);
	}
}
