package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.auth.dto.email.EmailVerificationCheckRequestDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

@ExtendWith(MockitoExtension.class)
class EmailAuthServiceImplTest {

	@InjectMocks
	private EmailAuthServiceImpl emailAuthService;

	@Mock
	private CommonAuthRepository commonAuthRepository;

	@Test
	@DisplayName("이미 가입된 이메일이면 EXISTED_EMAIL 예외가 발생한다")
	void checkEmailAvailability_ExistingEmail_ThrowsException() {
		// 1. Given
		EmailVerificationCheckRequestDTO request = new EmailVerificationCheckRequestDTO("dup@assu.com");
		when(commonAuthRepository.existsByEmail("dup@assu.com")).thenReturn(true);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> emailAuthService.checkEmailAvailability(request));

		// 3. Then
		assertEquals(ErrorStatus.EXISTED_EMAIL, exception.getCode());
	}

	@Test
	@DisplayName("가입되지 않은 이메일이면 예외 없이 통과한다")
	void checkEmailAvailability_NewEmail_Passes() {
		// 1. Given
		EmailVerificationCheckRequestDTO request = new EmailVerificationCheckRequestDTO("new@assu.com");
		when(commonAuthRepository.existsByEmail("new@assu.com")).thenReturn(false);

		// 2. When & Then
		assertDoesNotThrow(() -> emailAuthService.checkEmailAvailability(request));
	}
}
