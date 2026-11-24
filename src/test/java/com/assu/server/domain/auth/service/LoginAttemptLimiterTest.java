package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class LoginAttemptLimiterTest {

	private static final String COMMON_KEY = "login-fail:common:user@assu.com";
	private static final String BACKOFFICE_KEY = "login-fail:backoffice:user@assu.com";

	private LoginAttemptLimiter loginAttemptLimiter;

	private MeterRegistry meterRegistry;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		loginAttemptLimiter = new LoginAttemptLimiter(redisTemplate, meterRegistry);
	}

	@Test
	@DisplayName("첫 시도는 통과하고 이메일을 정규화한 키에 15분 TTL을 건다")
	void checkAttempt_whenFirstAttempt_thenPassesAndStartsWindow() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(COMMON_KEY)).thenReturn(1L);

		// when
		assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt(LoginAttemptLimiter.Policy.COMMON, " User@ASSU.com "));

		// then
		verify(redisTemplate).expire(COMMON_KEY, Duration.ofMinutes(15));
	}

	@Test
	@DisplayName("5번째 시도는 통과하되 실패로 끝날 경우에 대비해 차단 시간만큼 TTL을 다시 건다")
	void checkAttempt_whenFifthAttempt_thenPassesAndStartsBlockDuration() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(BACKOFFICE_KEY)).thenReturn(5L);

		// when
		assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt(LoginAttemptLimiter.Policy.BACKOFFICE, "user@assu.com"));

		// then
		verify(redisTemplate).expire(BACKOFFICE_KEY, Duration.ofMinutes(30));
	}

	@Test
	@DisplayName("5번 실패한 뒤의 시도는 TTL을 연장하지 않고 LOGIN_ATTEMPTS_EXCEEDED 예외와 차단 지표를 남긴다")
	void checkAttempt_whenSixthAttempt_thenThrowsAndCountsBlocked() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(COMMON_KEY)).thenReturn(6L);
		when(redisTemplate.getExpire(COMMON_KEY)).thenReturn(600L);

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> loginAttemptLimiter.checkAttempt(LoginAttemptLimiter.Policy.COMMON, "user@assu.com"));

		// then
		assertEquals(ErrorStatus.LOGIN_ATTEMPTS_EXCEEDED, exception.getCode());
		assertEquals(1.0, meterRegistry.counter("auth.login.blocked", "realm", "common").count());
		verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
	}

	@Test
	@DisplayName("차단 상태인데 TTL이 없는 키는 차단 시간만큼 TTL을 복구한다")
	void checkAttempt_whenBlockedKeyHasNoTtl_thenRestoresTtl() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(COMMON_KEY)).thenReturn(9L);
		when(redisTemplate.getExpire(COMMON_KEY)).thenReturn(-1L);

		// when
		assertThrows(CustomAuthException.class,
			() -> loginAttemptLimiter.checkAttempt(LoginAttemptLimiter.Policy.COMMON, "user@assu.com"));

		// then
		verify(redisTemplate).expire(COMMON_KEY, Duration.ofMinutes(15));
	}

	@Test
	@DisplayName("Redis 장애 시 로그인을 막지 않고 fail-open 지표를 남긴다")
	void checkAttempt_whenRedisUnavailable_thenFailsOpen() {
		// given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(COMMON_KEY)).thenThrow(new RedisConnectionFailureException("down"));

		// when
		assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt(LoginAttemptLimiter.Policy.COMMON, "user@assu.com"));

		// then
		assertEquals(1.0, meterRegistry.counter("auth.login.limit.failopen").count());
		assertEquals(0.0, meterRegistry.counter("auth.login.blocked", "realm", "common").count());
	}

	@Test
	@DisplayName("초기화는 해당 계정의 시도 횟수 키를 삭제하고 Redis 장애 시에도 예외를 던지지 않는다")
	void reset_whenCalled_thenDeletesKeyAndToleratesRedisFailure() {
		// given
		when(redisTemplate.delete(COMMON_KEY)).thenThrow(new RedisConnectionFailureException("down"));

		// when
		assertDoesNotThrow(() -> loginAttemptLimiter.reset(LoginAttemptLimiter.Policy.COMMON, "user@assu.com"));

		// then
		verify(redisTemplate).delete(COMMON_KEY);
		assertEquals(1.0, meterRegistry.counter("auth.login.limit.failopen").count());
	}
}
