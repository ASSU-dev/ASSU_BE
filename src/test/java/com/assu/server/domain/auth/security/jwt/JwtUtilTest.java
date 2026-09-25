package com.assu.server.domain.auth.security.jwt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

	@InjectMocks
	private JwtUtil jwtUtil;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private RedisTemplate<String, String> redisTemplate;

	@Spy
	private MeterRegistry meterRegistry = new SimpleMeterRegistry();

	private static final String SECRET_KEY = "test-secret-key-for-jwt-util-unit-test-1234567890";

	@BeforeEach
	void setUp() {
		jwtUtil.secretKey = SECRET_KEY;
	}

	private String buildAccessToken(String jti) {
		return Jwts.builder()
			.setClaims(Map.of("userId", 1, "role", "STUDENT"))
			.setId(jti)
			.setIssuedAt(new Date())
			.setExpiration(Date.from(ZonedDateTime.now().plusSeconds(3600).toInstant()))
			.signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	@Test
	@DisplayName("Redis 조회 중 예외가 발생하면 블랙리스트 판정을 통과시킨다(fail-open)")
	void assertNotBlacklisted_RedisFails_PassesThrough() {
		// 1. Given
		String jti = UUID.randomUUID().toString();
		String accessToken = buildAccessToken(jti);
		when(redisTemplate.hasKey("blacklist:" + jti))
			.thenThrow(new RedisConnectionFailureException("Redis 연결 실패"));

		// 2. When & Then
		assertDoesNotThrow(() -> jwtUtil.assertNotBlacklisted(accessToken));
		assertEquals(1.0, meterRegistry.counter("auth.blacklist.failopen").count());
	}

	@Test
	@DisplayName("Redis 조회가 정상이고 토큰이 블랙리스트에 있으면 여전히 차단한다")
	void assertNotBlacklisted_ActuallyBlacklisted_ThrowsException() {
		// 1. Given
		String jti = UUID.randomUUID().toString();
		String accessToken = buildAccessToken(jti);
		when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(true);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> jwtUtil.assertNotBlacklisted(accessToken));

		// 3. Then
		assertEquals(ErrorStatus.LOGOUT_USER, exception.getCode());
		assertEquals(0.0, meterRegistry.counter("auth.blacklist.failopen").count());
	}
}
