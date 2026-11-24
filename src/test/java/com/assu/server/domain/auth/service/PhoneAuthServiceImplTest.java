package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.aligo.client.AligoSmsClient;
import com.assu.server.infra.aligo.dto.AligoSendResponse;
import com.assu.server.infra.aligo.exception.AligoException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class PhoneAuthServiceImplTest {

	private PhoneAuthServiceImpl phoneAuthService;

	private MeterRegistry meterRegistry;

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
	private static final String MASTER_PHONE = "01000000000";
	private static final String MASTER_AUTH_NUMBER = "123456";
	private static final String VERIFY_FAILURE_KEY = "phone-verify-fail:" + PHONE;
	private static final String SEND_COOLDOWN_KEY = "phone-send-cooldown:" + PHONE;
	private static final String SEND_COUNT_KEY = "phone-send-count:" + PHONE;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		phoneAuthService = new PhoneAuthServiceImpl(
			redisTemplate, aligoSmsClient, partnerRepository, adminRepository, meterRegistry);
	}

	private AligoSendResponse aligoResponse(String resultCode) {
		AligoSendResponse response = new AligoSendResponse();
		response.setResult_code(resultCode);
		return response;
	}

	@Test
	@DisplayName("이미 가입된 전화번호로 인증번호를 요청하면 EXISTED_PHONE 예외가 발생한다")
	void checkAndSendAuthNumber_ExistingPhone_ThrowsException() {
		// 1. Given (파트너로 이미 가입된 번호)
		when(partnerRepository.existsByPhoneNumAndMember_DeletedAtIsNull(PHONE)).thenReturn(true);

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
		when(partnerRepository.existsByPhoneNumAndMember_DeletedAtIsNull(PHONE)).thenReturn(false);
		when(adminRepository.existsByPhoneNumAndMember_DeletedAtIsNull(PHONE)).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent("phone-send-cooldown:" + PHONE, "1", Duration.ofSeconds(60))).thenReturn(true);
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
		verify(redisTemplate, never()).delete(SEND_COOLDOWN_KEY);
		verify(redisTemplate).delete(VERIFY_FAILURE_KEY);
	}

	@Test
	@DisplayName("첫 발송에 성공하면 발송 횟수를 세고 24시간 TTL을 건다")
	void checkAndSendAuthNumber_whenFirstSendSucceeds_thenCountsSendWithDailyWindow() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(SEND_COOLDOWN_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
		when(redisTemplate.execute(any(RedisScript.class), eq(List.of(SEND_COUNT_KEY)), eq("86400"))).thenReturn(1L);
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenReturn(aligoResponse("1"));

		// when
		phoneAuthService.checkAndSendAuthNumber(PHONE);

		// then
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(SEND_COUNT_KEY)), eq("86400"));
	}

	@Test
	@DisplayName("60초 안에 다시 요청하면 SMS를 보내지 않고 PHONE_AUTH_SEND_COOLDOWN 예외가 발생한다")
	void checkAndSendAuthNumber_whenWithinCooldown_thenThrowsWithoutSending() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(SEND_COOLDOWN_KEY, "1", Duration.ofSeconds(60))).thenReturn(false);

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals(ErrorStatus.PHONE_AUTH_SEND_COOLDOWN, exception.getCode());
		verify(aligoSmsClient, never()).sendSms(anyString(), anyString(), anyString());
		verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
		assertEquals(1.0, meterRegistry.counter("auth.phone.blocked", "reason", "cooldown").count());
	}

	@Test
	@DisplayName("24시간 동안 10번 발송한 번호는 SMS를 보내지 않고 PHONE_AUTH_SEND_LIMIT_EXCEEDED 예외가 발생한다")
	void checkAndSendAuthNumber_whenSendLimitReached_thenThrowsWithoutSending() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(SEND_COOLDOWN_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
		when(valueOperations.get(SEND_COUNT_KEY)).thenReturn("10");

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals(ErrorStatus.PHONE_AUTH_SEND_LIMIT_EXCEEDED, exception.getCode());
		verify(aligoSmsClient, never()).sendSms(anyString(), anyString(), anyString());
		verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
		assertEquals(1.0, meterRegistry.counter("auth.phone.blocked", "reason", "send-limit").count());
	}

	@Test
	@DisplayName("SMS 발송이 실패하면 저장했던 인증번호를 삭제하고 FAILED_TO_SEND_SMS 예외가 발생한다")
	void checkAndSendAuthNumber_SmsFailed_DeletesCodeAndThrows() {
		// 1. Given
		when(partnerRepository.existsByPhoneNumAndMember_DeletedAtIsNull(PHONE)).thenReturn(false);
		when(adminRepository.existsByPhoneNumAndMember_DeletedAtIsNull(PHONE)).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent("phone-send-cooldown:" + PHONE, "1", Duration.ofSeconds(60))).thenReturn(true);
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenReturn(aligoResponse("-101"));

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// 3. Then
		assertEquals(ErrorStatus.FAILED_TO_SEND_SMS, exception.getCode());
		verify(redisTemplate, times(1)).delete(PHONE);
		verify(redisTemplate, times(1)).delete(SEND_COOLDOWN_KEY);
		verify(redisTemplate, never()).execute(any(RedisScript.class), eq(List.of(SEND_COUNT_KEY)), any());
	}

	@Test
	@DisplayName("SMS 발송 중 알리고 예외가 발생하면 저장했던 인증번호를 삭제하고 예외를 전파한다")
	void checkAndSendAuthNumber_SmsThrowsAligoException_DeletesCodeAndRethrows() {
		// 1. Given
		when(partnerRepository.existsByPhoneNumAndMember_DeletedAtIsNull(PHONE)).thenReturn(false);
		when(adminRepository.existsByPhoneNumAndMember_DeletedAtIsNull(PHONE)).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent("phone-send-cooldown:" + PHONE, "1", Duration.ofSeconds(60))).thenReturn(true);
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString()))
			.thenThrow(new AligoException(ErrorStatus.FAILED_TO_PARSE_ALIGO));

		// 2. When
		AligoException exception = assertThrows(AligoException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// 3. Then
		assertEquals(ErrorStatus.FAILED_TO_PARSE_ALIGO, exception.getCode());
		verify(redisTemplate, times(1)).delete(PHONE);
		verify(redisTemplate, times(1)).delete(SEND_COOLDOWN_KEY);
		verify(redisTemplate, never()).execute(any(RedisScript.class), eq(List.of(SEND_COUNT_KEY)), any());
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
		verify(redisTemplate, times(1)).delete(VERIFY_FAILURE_KEY);
	}

	@Test
	@DisplayName("인증번호가 처음 틀리면 실패 횟수에 인증번호와 같은 5분 TTL을 건다")
	void verifyAuthNumber_whenFirstMismatch_thenStartsFailureCount() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(PHONE)).thenReturn("654321");
		when(redisTemplate.execute(any(RedisScript.class), eq(List.of(VERIFY_FAILURE_KEY)), eq("300"))).thenReturn(1L);

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, exception.getCode());
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(VERIFY_FAILURE_KEY)), eq("300"));
		verify(redisTemplate, never()).delete(anyString());
	}

	@Test
	@DisplayName("인증번호를 5번 틀리면 저장된 인증번호를 폐기하고 PHONE_AUTH_ATTEMPTS_EXCEEDED 예외가 발생한다")
	void verifyAuthNumber_whenFifthMismatch_thenDeletesCodeAndThrowsAttemptsExceeded() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(PHONE)).thenReturn("654321");
		when(redisTemplate.execute(any(RedisScript.class), eq(List.of(VERIFY_FAILURE_KEY)), eq("300"))).thenReturn(5L);

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// then
		assertEquals(ErrorStatus.PHONE_AUTH_ATTEMPTS_EXCEEDED, exception.getCode());
		verify(redisTemplate).delete(PHONE);
		verify(redisTemplate).delete(VERIFY_FAILURE_KEY);
		verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
		assertEquals(1.0, meterRegistry.counter("auth.phone.blocked", "reason", "verify-attempts").count());
	}

	@Test
	@DisplayName("인증번호 검증에 성공하면 가입 시 확인할 인증 표식을 30분 TTL로 저장한다")
	void verifyAuthNumber_Success_StoresVerificationMarker() {
		// 1. Given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(PHONE)).thenReturn("123456");

		// 2. When
		phoneAuthService.verifyAuthNumber(PHONE, "123456");

		// 3. Then
		verify(valueOperations, times(1))
			.set("phone-verified:" + PHONE, "1", Duration.ofMinutes(30));
	}

	@Test
	@DisplayName("인증 표식이 있으면 소비 후 통과한다")
	void consumeVerification_Verified_DeletesMarker() {
		// 1. Given
		when(redisTemplate.delete("phone-verified:" + PHONE)).thenReturn(true);

		// 2. When
		assertDoesNotThrow(() -> phoneAuthService.consumeVerification(PHONE));

		// 3. Then
		verify(redisTemplate, times(1)).delete("phone-verified:" + PHONE);
	}

	@Test
	@DisplayName("인증 표식이 없으면 NOT_VERIFIED_PHONE_NUMBER 예외가 발생한다")
	void consumeVerification_NotVerified_ThrowsException() {
		// 1. Given (인증을 건너뛰었거나 30분이 지나 만료된 경우)
		when(redisTemplate.delete("phone-verified:" + PHONE)).thenReturn(false);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.consumeVerification(PHONE));

		// 3. Then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, exception.getCode());
	}

	@Test
	@DisplayName("마스터키 활성화 상태에서 마스터 번호로 인증번호를 요청하면 중복 체크와 SMS 발송 없이 통과한다")
	void checkAndSendAuthNumber_MasterPhoneNumberAndEnabled_SkipsCheckAndSms() {
		// 1. Given
		ReflectionTestUtils.setField(phoneAuthService, "masterKeyEnabled", true);

		// 2. When
		assertDoesNotThrow(() -> phoneAuthService.checkAndSendAuthNumber(MASTER_PHONE));

		// 3. Then
		verify(partnerRepository, never()).existsByPhoneNumAndMember_DeletedAtIsNull(anyString());
		verify(adminRepository, never()).existsByPhoneNumAndMember_DeletedAtIsNull(anyString());
		verify(aligoSmsClient, never()).sendSms(anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("마스터키가 비활성화 상태면 마스터 번호도 일반 번호와 동일하게 처리된다")
	void checkAndSendAuthNumber_MasterPhoneNumberButDisabled_FollowsNormalFlow() {
		// 1. Given
		when(partnerRepository.existsByPhoneNumAndMember_DeletedAtIsNull(MASTER_PHONE)).thenReturn(false);
		when(adminRepository.existsByPhoneNumAndMember_DeletedAtIsNull(MASTER_PHONE)).thenReturn(false);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent("phone-send-cooldown:" + MASTER_PHONE, "1", Duration.ofSeconds(60))).thenReturn(true);
		when(aligoSmsClient.sendSms(eq(MASTER_PHONE), anyString(), anyString())).thenReturn(aligoResponse("1"));

		// 2. When
		phoneAuthService.checkAndSendAuthNumber(MASTER_PHONE);

		// 3. Then
		verify(aligoSmsClient).sendSms(eq(MASTER_PHONE), anyString(), anyString());
	}

	@Test
	@DisplayName("마스터키 활성화 상태에서 고정 인증번호로 검증하면 Redis 조회 없이 인증 완료 표식이 저장된다")
	void verifyAuthNumber_MasterPhoneNumberAndEnabled_CorrectCode_MarksVerified() {
		// 1. Given
		ReflectionTestUtils.setField(phoneAuthService, "masterKeyEnabled", true);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		// 2. When
		assertDoesNotThrow(() -> phoneAuthService.verifyAuthNumber(MASTER_PHONE, MASTER_AUTH_NUMBER));

		// 3. Then
		verify(valueOperations).set("phone-verified:" + MASTER_PHONE, "1", Duration.ofMinutes(30));
		verify(redisTemplate, never()).delete(MASTER_PHONE);
	}

	@Test
	@DisplayName("마스터키 활성화 상태에서 고정 인증번호가 틀리면 NOT_VERIFIED_PHONE_NUMBER 예외가 발생한다")
	void verifyAuthNumber_MasterPhoneNumberAndEnabled_WrongCode_ThrowsException() {
		// 1. Given
		ReflectionTestUtils.setField(phoneAuthService, "masterKeyEnabled", true);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(MASTER_PHONE, "000000"));

		// 3. Then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, exception.getCode());
	}

	@Test
	@DisplayName("마스터키가 비활성화 상태면 고정 인증번호를 넣어도 Redis에 저장된 값과 비교한다")
	void verifyAuthNumber_MasterPhoneNumberButDisabled_FollowsNormalFlow() {
		// 1. Given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(MASTER_PHONE)).thenReturn(null);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(MASTER_PHONE, MASTER_AUTH_NUMBER));

		// 3. Then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, exception.getCode());
	}

	@Test
	@DisplayName("isMasterPhoneNumber는 마스터키 활성화 및 번호 일치 여부에 따라 판별한다")
	void isMasterPhoneNumber_ChecksEnabledFlagAndNumber() {
		// 1. Given
		ReflectionTestUtils.setField(phoneAuthService, "masterKeyEnabled", true);

		// 2. When & Then
		assertTrue(phoneAuthService.isMasterPhoneNumber(MASTER_PHONE));
		assertFalse(phoneAuthService.isMasterPhoneNumber(PHONE));

		ReflectionTestUtils.setField(phoneAuthService, "masterKeyEnabled", false);
		assertFalse(phoneAuthService.isMasterPhoneNumber(MASTER_PHONE));
	}
}
