package com.assu.server.domain.auth.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.auth.dto.signup.StudentTokenSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.student.StudentTokenAuthPayloadDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.service.EmailAuthService;
import com.assu.server.domain.auth.service.LoginService;
import com.assu.server.domain.auth.service.LogoutService;
import com.assu.server.domain.auth.service.PhoneAuthService;
import com.assu.server.domain.auth.service.SSUAuthService;
import com.assu.server.domain.auth.service.SignUpService;
import com.assu.server.domain.auth.service.WithdrawalService;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

@ExtendWith(MockitoExtension.class)
class AuthControllerSsuAuthTest {

	private static final StudentTokenAuthPayloadDTO PAYLOAD =
		new StudentTokenAuthPayloadDTO("sToken", "20211438", University.SSU);
	private static final USaintAuthResponseDTO AUTH_RESPONSE =
		USaintAuthResponseDTO.of("20211438", "홍길동", "재학", "4학년 1학기", "컴퓨터학부");

	@Mock
	private PhoneAuthService phoneAuthService;

	@Mock
	private EmailAuthService emailAuthService;

	@Mock
	private SignUpService signUpService;

	@Mock
	private LoginService loginService;

	@Mock
	private LogoutService logoutService;

	@Mock
	private SSUAuthService ssuAuthService;

	@Mock
	private WithdrawalService withdrawalService;

	@InjectMocks
	private AuthController authController;

	@Test
	@DisplayName("학생 로그인은 유세인트 인증을 먼저 끝낸 뒤 그 결과로 로그인 서비스를 호출한다")
	void loginStudent_AuthenticatesWithUSaintBeforeLoginService() {
		// given
		when(ssuAuthService.uSaintAuth(any(USaintAuthRequestDTO.class))).thenReturn(AUTH_RESPONSE);

		// when
		authController.loginStudent(PAYLOAD);

		// then
		InOrder inOrder = inOrder(ssuAuthService, loginService);
		inOrder.verify(ssuAuthService).uSaintAuth(new USaintAuthRequestDTO("sToken", "20211438"));
		inOrder.verify(loginService).loginSsuStudent(AUTH_RESPONSE);
	}

	@Test
	@DisplayName("유세인트 인증이 실패하면 로그인 서비스(트랜잭션)를 호출하지 않는다")
	void loginStudent_USaintFails_DoesNotCallLoginService() {
		// given
		when(ssuAuthService.uSaintAuth(any(USaintAuthRequestDTO.class)))
			.thenThrow(new CustomAuthException(ErrorStatus.SSU_SAINT_SSO_FAILED));

		// when
		assertThrows(CustomAuthException.class, () -> authController.loginStudent(PAYLOAD));

		// then
		verifyNoInteractions(loginService);
	}

	@Test
	@DisplayName("학생 회원가입은 유세인트 인증을 먼저 끝낸 뒤 그 결과로 가입 서비스를 호출한다")
	void signupStudent_AuthenticatesWithUSaintBeforeSignUpService() {
		// given
		StudentTokenSignUpRequestDTO request = new StudentTokenSignUpRequestDTO(true, true, PAYLOAD);
		when(ssuAuthService.uSaintAuth(any(USaintAuthRequestDTO.class))).thenReturn(AUTH_RESPONSE);

		// when
		authController.signupStudent(request);

		// then
		InOrder inOrder = inOrder(ssuAuthService, signUpService);
		inOrder.verify(ssuAuthService).uSaintAuth(new USaintAuthRequestDTO("sToken", "20211438"));
		inOrder.verify(signUpService).signupSsuStudent(request, AUTH_RESPONSE);
	}

	@Test
	@DisplayName("유세인트 인증이 실패하면 가입 서비스(트랜잭션)를 호출하지 않는다")
	void signupStudent_USaintFails_DoesNotCallSignUpService() {
		// given
		StudentTokenSignUpRequestDTO request = new StudentTokenSignUpRequestDTO(true, true, PAYLOAD);
		when(ssuAuthService.uSaintAuth(any(USaintAuthRequestDTO.class)))
			.thenThrow(new CustomAuthException(ErrorStatus.SSU_SAINT_SSO_FAILED));

		// when
		assertThrows(CustomAuthException.class, () -> authController.signupStudent(request));

		// then
		verifyNoInteractions(signUpService);
	}
}
