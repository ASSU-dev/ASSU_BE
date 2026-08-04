package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.LoginResponseDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.auth.security.userdetails.CommonUserDetails;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

	private LoginServiceImpl loginService;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private SSUAuthService ssuAuthService;

	@Mock
	private StudentRepository studentRepository;

	@Mock
	private RealmAuthAdapter commonAdapter;

	@BeforeEach
	void setUp() {
		loginService = new LoginServiceImpl(
			authenticationManager, jwtUtil, ssuAuthService, studentRepository, List.of(commonAdapter));
	}

	@Test
	@DisplayName("백오피스 계정이 공통 로그인을 시도하면 BACKOFFICE_USE_DEDICATED_LOGIN 예외가 발생한다")
	void loginCommon_BackofficeAccount_ThrowsException() {
		// 1. Given
		CommonLoginRequestDTO request = new CommonLoginRequestDTO("back@assu.com", "password1!");

		CommonUserDetails userDetails = new CommonUserDetails(
			"back@assu.com", "encoded", true, List.of(), ActivationStatus.ACTIVE, UserRole.BACKOFFICE);

		Authentication authentication = mock(Authentication.class);
		when(authentication.getPrincipal()).thenReturn(userDetails);
		when(authenticationManager.authenticate(any())).thenReturn(authentication);

		when(commonAdapter.supports(AuthRealm.COMMON)).thenReturn(true);

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> loginService.loginCommon(request));

		// 3. Then
		assertEquals(ErrorStatus.BACKOFFICE_USE_DEDICATED_LOGIN, exception.getCode());
		verify(jwtUtil, never()).issueTokens(any(), any(), any(), any());
	}

	@Test
	@DisplayName("공통 로그인 성공 시 회원 정보와 발급된 토큰이 담긴 응답을 반환한다")
	void loginCommon_Success_ReturnsTokens() {
		// 1. Given
		CommonLoginRequestDTO request = new CommonLoginRequestDTO("partner@assu.com", "password1!");

		CommonUserDetails userDetails = new CommonUserDetails(
			"partner@assu.com", "encoded", true, List.of(), ActivationStatus.ACTIVE, UserRole.PARTNER);

		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("partner@assu.com");
		when(authentication.getPrincipal()).thenReturn(userDetails);
		when(authenticationManager.authenticate(any())).thenReturn(authentication);

		when(commonAdapter.supports(AuthRealm.COMMON)).thenReturn(true);
		when(commonAdapter.authRealmValue()).thenReturn("COMMON");

		Member member = mock(Member.class);
		when(member.getId()).thenReturn(5L);
		when(member.getRole()).thenReturn(UserRole.PARTNER);
		when(member.getIsActivated()).thenReturn(ActivationStatus.ACTIVE);
		when(commonAdapter.loadMember("partner@assu.com")).thenReturn(member);

		TokensDTO tokens = TokensDTO.of("access-token", "refresh-token");
		when(jwtUtil.issueTokens(5L, "partner@assu.com", UserRole.PARTNER, "COMMON")).thenReturn(tokens);

		// 2. When
		LoginResponseDTO response = loginService.loginCommon(request);

		// 3. Then
		assertEquals(5L, response.memberId());
		assertEquals(UserRole.PARTNER, response.role());
		assertEquals(ActivationStatus.ACTIVE, response.status());
		assertEquals("access-token", response.tokens().accessToken());
		assertEquals("refresh-token", response.tokens().refreshToken());
	}

	@Test
	@DisplayName("백오피스 전용 Refresh 토큰으로 일반 재발급을 시도하면 BACKOFFICE_USE_DEDICATED_LOGIN 예외가 발생한다")
	void refresh_BackofficeToken_ThrowsException() {
		// 1. Given
		String refreshToken = "backoffice-refresh-token";
		Claims claims = mock(Claims.class);
		when(jwtUtil.validateTokenOnlySignature(refreshToken)).thenReturn(claims);
		when(jwtUtil.isBackofficeAudience(claims)).thenReturn(true);

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> loginService.refresh(refreshToken));

		// 3. Then
		assertEquals(ErrorStatus.BACKOFFICE_USE_DEDICATED_LOGIN, exception.getCode());
		verify(jwtUtil, never()).rotateRefreshToken(any());
	}

	@Test
	@DisplayName("Refresh 토큰 재발급 성공 시 회전된 새 토큰과 회원 ID를 반환한다")
	void refresh_Success_ReturnsRotatedTokens() {
		// 1. Given
		String refreshToken = "old-refresh-token";

		Claims refreshClaims = mock(Claims.class);
		when(jwtUtil.validateTokenOnlySignature(refreshToken)).thenReturn(refreshClaims);
		when(jwtUtil.isBackofficeAudience(refreshClaims)).thenReturn(false);

		TokensDTO rotated = TokensDTO.of("new-access-token", "new-refresh-token");
		when(jwtUtil.rotateRefreshToken(refreshToken)).thenReturn(rotated);

		Claims newAccessClaims = mock(Claims.class);
		when(newAccessClaims.get("userId")).thenReturn(5);
		when(jwtUtil.validateTokenOnlySignature("new-access-token")).thenReturn(newAccessClaims);

		// 2. When
		RefreshResponseDTO response = loginService.refresh(refreshToken);

		// 3. Then
		assertEquals(5L, response.memberId());
		assertEquals("new-access-token", response.newAccess());
		assertEquals("new-refresh-token", response.newRefresh());
	}
}
