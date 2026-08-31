package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.aligo.client.AligoSmsClient;
import com.assu.server.infra.aligo.dto.AligoSendResponse;
import com.assu.server.infra.aligo.exception.AligoException;

@ExtendWith(MockitoExtension.class)
class PhoneAuthServiceImplTest {

	@InjectMocks
	private PhoneAuthServiceImpl phoneAuthService;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private AligoSmsClient aligoSmsClient;

	@Mock
	private PartnerRepository partnerRepository;

	@Mock
	private AdminRepository adminRepository;

	private static final String PHONE = "01012345678";

	private AligoSendResponse aligoResponse(String resultCode) {
		AligoSendResponse response = new AligoSendResponse();
		response.setResult_code(resultCode);
		return response;
	}

	@Test
	@DisplayName("이미 가입된 전화번호로 인증번호를 요청하면 EXISTED_PHONE 예외가 발생한다")
	void checkAndSendAuthNumber_ExistingPhone_ThrowsException() {
		// 1. Given (파트너로 이미 가입된 번호)
		when(partnerRepository.existsByPhoneNum(PHONE)).thenReturn(true);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// 3. Then
		assertEquals(ErrorStatus.EXISTED_PHONE, exception.getCode());
		verify(aligoSmsClient, never()).sendSms(anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("인증번호 발송 성공 시 6자리 인증번호가 5분 TTL로 Redis에 저장되고 SMS로 전송된다")
	void checkAndSendAuthNumber_Success_StoresCodeAndSendsSms() {
		// 1. Given
		when(partnerRepository.existsByPhoneNum(PHONE)).thenReturn(false);
		when(adminRepository.existsByPhoneNum(PHONE)).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenReturn(aligoResponse("1"));

		// 2. When
		phoneAuthService.checkAndSendAuthNumber(PHONE);

		// 3. Then (저장된 인증번호와 SMS 메시지의 인증번호가 일치하는지 검증)
		ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(eq(PHONE), codeCaptor.capture(), eq(Duration.ofMinutes(5)));

		String authNumber = codeCaptor.getValue();
		assertEquals(6, authNumber.length());
		assertTrue(authNumber.chars().allMatch(Character::isDigit));

		verify(aligoSmsClient).sendSms(eq(PHONE), eq("[ASSU] 인증번호: " + authNumber), anyString());
		verify(redisTemplate, never()).delete(PHONE);
	}

	@Test
	@DisplayName("SMS 발송이 실패하면 저장했던 인증번호를 삭제하고 FAILED_TO_SEND_SMS 예외가 발생한다")
	void checkAndSendAuthNumber_SmsFailed_DeletesCodeAndThrows() {
		// 1. Given
		when(partnerRepository.existsByPhoneNum(PHONE)).thenReturn(false);
		when(adminRepository.existsByPhoneNum(PHONE)).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenReturn(aligoResponse("-101"));

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// 3. Then
		assertEquals(ErrorStatus.FAILED_TO_SEND_SMS, exception.getCode());
		verify(redisTemplate, times(1)).delete(PHONE);
	}

	@Test
	@DisplayName("SMS 발송 중 알리고 예외가 발생하면 저장했던 인증번호를 삭제하고 예외를 전파한다")
	void checkAndSendAuthNumber_SmsThrowsAligoException_DeletesCodeAndRethrows() {
		// 1. Given
		when(partnerRepository.existsByPhoneNum(PHONE)).thenReturn(false);
		when(adminRepository.existsByPhoneNum(PHONE)).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString()))
			.thenThrow(new AligoException(ErrorStatus.FAILED_TO_PARSE_ALIGO));

		// 2. When
		AligoException exception = assertThrows(AligoException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// 3. Then
		assertEquals(ErrorStatus.FAILED_TO_PARSE_ALIGO, exception.getCode());
		verify(redisTemplate, times(1)).delete(PHONE);
	}

	@Test
	@DisplayName("저장된 인증번호가 없으면 NOT_VERIFIED_PHONE_NUMBER 예외가 발생한다")
	void verifyAuthNumber_NoStoredCode_ThrowsException() {
		// 1. Given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(PHONE)).thenReturn(null);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// 3. Then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, exception.getCode());
		verify(redisTemplate, never()).delete(anyString());
	}

	@Test
	@DisplayName("인증번호가 일치하지 않으면 NOT_VERIFIED_PHONE_NUMBER 예외가 발생한다")
	void verifyAuthNumber_CodeMismatch_ThrowsException() {
		// 1. Given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(PHONE)).thenReturn("654321");

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// 3. Then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, exception.getCode());
		verify(redisTemplate, never()).delete(anyString());
	}

	@Test
	@DisplayName("인증번호가 일치하면 검증에 성공하고 Redis에서 인증번호를 삭제한다")
	void verifyAuthNumber_Success_DeletesCode() {
		// 1. Given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(PHONE)).thenReturn("123456");

		// 2. When
		assertDoesNotThrow(() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// 3. Then
		verify(redisTemplate, times(1)).delete(PHONE);
	}
}
