package com.assu.server.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class IpRateLimitInterceptorTest {

	private static final List<String> KEY = List.of("ip-limit:login:203.0.113.7");

	private IpRateLimitInterceptor interceptor;

	private MeterRegistry meterRegistry;

	@Mock
	private StringRedisTemplate redisTemplate;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		interceptor = new IpRateLimitInterceptor(redisTemplate, meterRegistry, "login", 30, Duration.ofMinutes(15));
	}

	private MockHttpServletRequest post(String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/commons/login");
		request.addHeader("X-Forwarded-For", forwardedFor);
		return request;
	}

	@Test
	@DisplayName("한도 이내 요청은 IP별 키로 창 길이와 함께 세고 통과시킨다")
	void preHandle_whenWithinLimit_thenPasses() {
		// given
		when(redisTemplate.execute(any(RedisScript.class), eq(KEY), eq("900"))).thenReturn(30L);

		// when & then
		assertTrue(interceptor.preHandle(post("203.0.113.7, 10.42.0.1"), new MockHttpServletResponse(), new Object()));
	}

	@Test
	@DisplayName("한도를 넘긴 요청은 AUTH_REQUESTS_EXCEEDED 예외와 차단 지표를 남긴다")
	void preHandle_whenOverLimit_thenThrowsAndCountsBlocked() {
		// given
		when(redisTemplate.execute(any(RedisScript.class), eq(KEY), eq("900"))).thenReturn(31L);

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> interceptor.preHandle(post("203.0.113.7"), new MockHttpServletResponse(), new Object()));

		// then
		assertEquals(ErrorStatus.AUTH_REQUESTS_EXCEEDED, exception.getCode());
		assertEquals(1.0, meterRegistry.counter("auth.ip.blocked", "bucket", "login").count());
	}

	@Test
	@DisplayName("사설·루프백·형식이 맞지 않는 주소와 POST가 아닌 요청은 세지 않는다")
	void preHandle_whenNotCountable_thenSkipsRedis() {
		// given
		MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/auth/commons/login");
		preflight.addHeader("X-Forwarded-For", "203.0.113.7");

		// when & then
		assertTrue(interceptor.preHandle(post("10.42.0.15"), new MockHttpServletResponse(), new Object()));
		assertTrue(interceptor.preHandle(post("127.0.0.1"), new MockHttpServletResponse(), new Object()));
		assertTrue(interceptor.preHandle(post("not-an-ip.example"), new MockHttpServletResponse(), new Object()));
		assertTrue(interceptor.preHandle(preflight, new MockHttpServletResponse(), new Object()));
		verifyNoInteractions(redisTemplate);
	}

	@Test
	@DisplayName("Redis 장애 시 요청을 막지 않고 fail-open 지표를 남긴다")
	void preHandle_whenRedisUnavailable_thenFailsOpen() {
		// given
		when(redisTemplate.execute(any(RedisScript.class), eq(KEY), eq("900")))
			.thenThrow(new RedisConnectionFailureException("down"));

		// when & then
		assertTrue(interceptor.preHandle(post("203.0.113.7"), new MockHttpServletResponse(), new Object()));
		assertEquals(1.0, meterRegistry.counter("auth.ip.limit.failopen").count());
	}
}
