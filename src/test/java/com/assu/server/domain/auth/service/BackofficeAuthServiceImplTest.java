package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class BackofficeAuthServiceImplTest {

	private static final String EMAIL = "operator@assu.com";

	private BackofficeAuthServiceImpl backofficeAuthService;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private LoginAttemptLimiter loginAttemptLimiter;

	@Mock
	private RealmAuthAdapter commonAdapter;

	@BeforeEach
	void setUp() {
		backofficeAuthService = new BackofficeAuthServiceImpl(
			authenticationManager, jwtUtil, loginAttemptLimiter, List.of(commonAdapter));
	}

	@Test
	@DisplayName("로그인 시도가 차단된 계정은 비밀번호를 확인하지 않고 LOGIN_ATTEMPTS_EXCEEDED 예외가 발생한다")
	void login_whenAttemptsExceeded_thenRejectsBeforeAuthentication() {
		// given
		CommonLoginRequestDTO request = new CommonLoginRequestDTO(EMAIL, "password1!");
		doThrow(new CustomAuthException(ErrorStatus.LOGIN_ATTEMPTS_EXCEEDED))
			.when(loginAttemptLimiter).checkAttempt(EMAIL);

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> backofficeAuthService.login(request));

		// then
		assertEquals(ErrorStatus.LOGIN_ATTEMPTS_EXCEEDED, exception.getCode());
		verify(authenticationManager, never()).authenticate(any());
	}

	@Test
	@DisplayName("운영자가 아닌 계정은 비밀번호가 맞아도 시도 횟수를 초기화하지 않는다")
	void login_whenNotBackofficeRole_thenDoesNotResetAttempts() {
		// given
		CommonLoginRequestDTO request = new CommonLoginRequestDTO(EMAIL, "password1!");

		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn(EMAIL);
		when(authenticationManager.authenticate(any())).thenReturn(authentication);

		Member member = mock(Member.class);
		when(member.getRole()).thenReturn(UserRole.PARTNER);
		when(commonAdapter.supports(AuthRealm.COMMON)).thenReturn(true);
		when(commonAdapter.loadMember(EMAIL)).thenReturn(member);

		// when
		GeneralException exception = assertThrows(GeneralException.class,
			() -> backofficeAuthService.login(request));

		// then
		assertEquals(ErrorStatus.NO_BACKOFFICE_TYPE, exception.getCode());
		verify(loginAttemptLimiter).checkAttempt(EMAIL);
		verify(loginAttemptLimiter, never()).reset(any());
	}
}
