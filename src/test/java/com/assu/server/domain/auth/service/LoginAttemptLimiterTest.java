package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class LoginAttemptLimiterTest {

	private static final List<String> KEY = List.of("login-fail:user@assu.com");

	private LoginAttemptLimiter loginAttemptLimiter;

	private MeterRegistry meterRegistry;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private CommonAuthRepository commonAuthRepository;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		loginAttemptLimiter = new LoginAttemptLimiter(redisTemplate, meterRegistry, commonAuthRepository);
	}

	@Test
	@DisplayName("일반 계정은 정규화한 이메일 키로 15분 창·5회·15분 차단을 한 번의 스크립트로 센다")
	void checkAttempt_whenCommonAccountWithinLimit_thenPassesWithCommonPolicy() {
		// given
		when(commonAuthRepository.findRoleByEmail(" User@ASSU.com ")).thenReturn(Optional.of(UserRole.PARTNER));
		when(redisTemplate.execute(any(RedisScript.class), eq(KEY), eq("900"), eq("5"), eq("900"))).thenReturn(5L);

		// when & then
		assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt(" User@ASSU.com "));
	}

	@Test
	@DisplayName("백오피스 계정은 어느 로그인 경로로 시도하든 같은 키에 30분 차단을 적용한다")
	void checkAttempt_whenBackofficeAccount_thenAppliesBackofficeBlockDuration() {
		// given
		when(commonAuthRepository.findRoleByEmail("user@assu.com")).thenReturn(Optional.of(UserRole.BACKOFFICE));
		when(redisTemplate.execute(any(RedisScript.class), eq(KEY), eq("900"), eq("5"), eq("1800"))).thenReturn(1L);

		// when & then
		assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt("user@assu.com"));
	}

	@Test
	@DisplayName("가입되지 않은 이메일도 일반 계정과 같은 기준으로 센다")
	void checkAttempt_whenUnknownEmail_thenUsesCommonPolicy() {
		// given
		when(commonAuthRepository.findRoleByEmail("user@assu.com")).thenReturn(Optional.empty());
		when(redisTemplate.execute(any(RedisScript.class), eq(KEY), eq("900"), eq("5"), eq("900"))).thenReturn(1L);

		// when & then
		assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt("user@assu.com"));
	}

	@Test
	@DisplayName("5번을 넘긴 시도는 LOGIN_ATTEMPTS_EXCEEDED 예외와 차단 지표를 남긴다")
	void checkAttempt_whenSixthAttempt_thenThrowsAndCountsBlocked() {
		// given
		when(commonAuthRepository.findRoleByEmail("user@assu.com")).thenReturn(Optional.empty());
		when(redisTemplate.execute(any(RedisScript.class), eq(KEY), eq("900"), eq("5"), eq("900"))).thenReturn(6L);

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> loginAttemptLimiter.checkAttempt("user@assu.com"));

		// then
		assertEquals(ErrorStatus.LOGIN_ATTEMPTS_EXCEEDED, exception.getCode());
		assertEquals(1.0, meterRegistry.counter("auth.login.blocked", "realm", "common").count());
	}

	@Test
	@DisplayName("Redis 장애 시 로그인을 막지 않고 fail-open 지표를 남긴다")
	void checkAttempt_whenRedisUnavailable_thenFailsOpen() {
		// given
		when(commonAuthRepository.findRoleByEmail("user@assu.com")).thenReturn(Optional.empty());
		when(redisTemplate.execute(any(RedisScript.class), eq(KEY), eq("900"), eq("5"), eq("900")))
			.thenThrow(new RedisConnectionFailureException("down"));

		// when
		assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt("user@assu.com"));

		// then
		assertEquals(1.0, meterRegistry.counter("auth.login.limit.failopen").count());
		assertEquals(0.0, meterRegistry.counter("auth.login.blocked", "realm", "common").count());
	}

	@Test
	@DisplayName("초기화는 해당 계정의 시도 횟수 키를 삭제하고 Redis 장애 시에도 예외를 던지지 않는다")
	void reset_whenCalled_thenDeletesKeyAndToleratesRedisFailure() {
		// given
		when(redisTemplate.delete(KEY.get(0))).thenThrow(new RedisConnectionFailureException("down"));

		// when
		assertDoesNotThrow(() -> loginAttemptLimiter.reset("user@assu.com"));

		// then
		verify(redisTemplate).delete(KEY.get(0));
		assertEquals(1.0, meterRegistry.counter("auth.login.limit.failopen").count());
	}
}
