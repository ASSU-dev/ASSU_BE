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

	private static final List<String> SEND_KEYS = List.of(SEND_COUNT_KEY, SEND_COOLDOWN_KEY, PHONE, VERIFY_FAILURE_KEY);

	private void givenReservation(List<String> keys, String result) {
		when(redisTemplate.execute(any(RedisScript.class), eq(keys),
			eq("10"), eq("60"), eq("86400"), anyString(), eq("300"))).thenReturn(result);
	}

	private String reservedAuthNumber(List<String> keys) {
		ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
		verify(redisTemplate).execute(any(RedisScript.class), eq(keys),
			eq("10"), eq("60"), eq("86400"), codeCaptor.capture(), eq("300"));
		return codeCaptor.getValue();
	}

	@Test
	@DisplayName("발송 전에 한도 확인·쿨다운 선점·횟수 예약·인증번호 저장을 한 번의 스크립트로 처리하고 그 인증번호를 SMS로 보낸다")
	void checkAndSendAuthNumber_whenReserved_thenSendsReservedCode() {
		// given
		givenReservation(SEND_KEYS, "RESERVED");
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenReturn(aligoResponse("1"));

		// when
		phoneAuthService.checkAndSendAuthNumber(PHONE);

		// then
		String authNumber = reservedAuthNumber(SEND_KEYS);
		assertTrue(authNumber.matches("\\d{6}"));
		verify(aligoSmsClient).sendSms(eq(PHONE), eq("[ASSU] 인증번호: " + authNumber), anyString());
		verify(redisTemplate, never()).execute(any(RedisScript.class), eq(List.of(SEND_COUNT_KEY, SEND_COOLDOWN_KEY, PHONE)), anyString());
		verify(redisTemplate, never()).opsForValue();
	}

	@Test
	@DisplayName("60초 안에 다시 요청하면 SMS를 보내지 않고 PHONE_AUTH_SEND_COOLDOWN 예외가 발생한다")
	void checkAndSendAuthNumber_whenWithinCooldown_thenThrowsWithoutSending() {
		// given
		givenReservation(SEND_KEYS, "COOLDOWN");

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals(ErrorStatus.PHONE_AUTH_SEND_COOLDOWN, exception.getCode());
		verify(aligoSmsClient, never()).sendSms(anyString(), anyString(), anyString());
		assertEquals(1.0, meterRegistry.counter("auth.phone.blocked", "reason", "cooldown").count());
	}

	@Test
	@DisplayName("24시간 동안 10번 발송한 번호는 SMS를 보내지 않고 PHONE_AUTH_SEND_LIMIT_EXCEEDED 예외가 발생한다")
	void checkAndSendAuthNumber_whenSendLimitReached_thenThrowsWithoutSending() {
		// given
		givenReservation(SEND_KEYS, "LIMIT");

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals(ErrorStatus.PHONE_AUTH_SEND_LIMIT_EXCEEDED, exception.getCode());
		verify(aligoSmsClient, never()).sendSms(anyString(), anyString(), anyString());
		assertEquals(1.0, meterRegistry.counter("auth.phone.blocked", "reason", "send-limit").count());
	}

	@Test
	@DisplayName("SMS 발송 결과가 실패면 이 요청의 예약을 되돌리고 FAILED_TO_SEND_SMS 예외가 발생한다")
	void checkAndSendAuthNumber_whenSmsResultFailed_thenReleasesReservation() {
		// given
		givenReservation(SEND_KEYS, "RESERVED");
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenReturn(aligoResponse("-101"));

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals(ErrorStatus.FAILED_TO_SEND_SMS, exception.getCode());
		String authNumber = reservedAuthNumber(SEND_KEYS);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(SEND_COUNT_KEY, SEND_COOLDOWN_KEY, PHONE)), eq(authNumber));
	}

	@Test
	@DisplayName("SMS 발송 중 알리고 예외가 발생하면 이 요청의 예약을 되돌리고 예외를 전파한다")
	void checkAndSendAuthNumber_whenAligoThrows_thenReleasesReservationAndRethrows() {
		// given
		givenReservation(SEND_KEYS, "RESERVED");
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString()))
			.thenThrow(new AligoException(ErrorStatus.FAILED_TO_PARSE_ALIGO));

		// when
		AligoException exception = assertThrows(AligoException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals(ErrorStatus.FAILED_TO_PARSE_ALIGO, exception.getCode());
		String authNumber = reservedAuthNumber(SEND_KEYS);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(SEND_COUNT_KEY, SEND_COOLDOWN_KEY, PHONE)), eq(authNumber));
	}

	@Test
	@DisplayName("SMS 발송 중 연결 실패·타임아웃 같은 런타임 예외가 나도 이 요청의 예약을 되돌리고 예외를 전파한다")
	void checkAndSendAuthNumber_whenClientThrowsRuntimeException_thenReleasesReservation() {
		// given
		givenReservation(SEND_KEYS, "RESERVED");
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString()))
			.thenThrow(new IllegalStateException("timeout"));

		// when
		assertThrows(IllegalStateException.class, () -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		String authNumber = reservedAuthNumber(SEND_KEYS);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(SEND_COUNT_KEY, SEND_COOLDOWN_KEY, PHONE)), eq(authNumber));
	}

	private void givenVerifyResult(String phone, String authNumber, String result) {
		when(redisTemplate.execute(any(RedisScript.class),
			eq(List.of(phone, "phone-verify-fail:" + phone, "phone-verified:" + phone)),
			eq(authNumber), eq("5"), eq("300"), eq("1800"))).thenReturn(result);
	}

	@Test
	@DisplayName("저장된 인증번호가 없으면 NOT_VERIFIED_PHONE_NUMBER 예외가 발생한다")
	void verifyAuthNumber_whenNoStoredCode_thenThrowsNotVerified() {
		// given
		givenVerifyResult(PHONE, "123456", "MISSING");

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, exception.getCode());
	}

	@Test
	@DisplayName("인증번호가 일치하지 않으면 NOT_VERIFIED_PHONE_NUMBER 예외가 발생한다")
	void verifyAuthNumber_whenMismatch_thenThrowsNotVerified() {
		// given
		givenVerifyResult(PHONE, "123456", "MISMATCH");

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, exception.getCode());
		assertEquals(0.0, meterRegistry.counter("auth.phone.blocked", "reason", "verify-attempts").count());
	}

	@Test
	@DisplayName("인증번호를 5번 틀려 폐기되면 PHONE_AUTH_ATTEMPTS_EXCEEDED 예외와 차단 지표를 남긴다")
	void verifyAuthNumber_whenExhausted_thenThrowsAttemptsExceeded() {
		// given
		givenVerifyResult(PHONE, "123456", "EXHAUSTED");

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// then
		assertEquals(ErrorStatus.PHONE_AUTH_ATTEMPTS_EXCEEDED, exception.getCode());
		assertEquals(1.0, meterRegistry.counter("auth.phone.blocked", "reason", "verify-attempts").count());
	}

	@Test
	@DisplayName("비교·실패 집계·폐기·인증 완료 표식 저장을 한 번의 스크립트로 처리하고 개별 Redis 명령은 쓰지 않는다")
	void verifyAuthNumber_whenVerified_thenHandlesAllStateInSingleScript() {
		// given
		givenVerifyResult(PHONE, "123456", "VERIFIED");

		// when
		assertDoesNotThrow(() -> phoneAuthService.verifyAuthNumber(PHONE, "123456"));

		// then
		verify(redisTemplate, never()).opsForValue();
		verify(redisTemplate, never()).delete(anyString());
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
		givenReservation(List.of("phone-send-count:" + MASTER_PHONE, "phone-send-cooldown:" + MASTER_PHONE,
			MASTER_PHONE, "phone-verify-fail:" + MASTER_PHONE), "RESERVED");
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
		givenVerifyResult(MASTER_PHONE, MASTER_AUTH_NUMBER, "MISSING");

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
