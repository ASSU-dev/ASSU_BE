package com.assu.server.domain.auth.security.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private RedisTemplate<String, String> redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private JwtUtil jwtUtil;

	private static final Long MEMBER_ID = 7L;
	private static final String REVOKED_BEFORE_KEY = "revoked-before:" + MEMBER_ID;

	@BeforeEach
	void setUp() {
		jwtUtil = new JwtUtil(memberRepository, redisTemplate);
		ReflectionTestUtils.setField(jwtUtil, "accessValidSeconds", 3600);
		ReflectionTestUtils.setField(jwtUtil, "backofficeAccessValidSeconds", 1800);
	}

	private Claims claimsIssuedAt(long issuedAtMillis) {
		Claims claims = mock(Claims.class);
		when(claims.get("userId")).thenReturn(MEMBER_ID.intValue());
		when(claims.get("iatMillis")).thenReturn(issuedAtMillis);
		return claims;
	}

	private Claims claimsWithMemberId() {
		Claims claims = mock(Claims.class);
		when(claims.get("userId")).thenReturn(MEMBER_ID.intValue());
		return claims;
	}

	@Test
	@DisplayName("회원 단위 Access 토큰 무효화 시 access/backoffice 유효 시간 중 큰 값을 TTL로 사용한다")
	void revokeAllAccessTokensSince_UsesLongerValidSecondsAsTtl() {
		// 1. Given
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		// 2. When
		jwtUtil.revokeAllAccessTokensSince(MEMBER_ID);

		// 3. Then
		verify(valueOperations).set(eq(REVOKED_BEFORE_KEY), anyString(), eq(3600L), eq(TimeUnit.SECONDS));
	}

	@Test
	@DisplayName("강제 탈퇴로 무효화된 시점 이전에 발급된 Access 토큰은 assertNotRevoked에서 거부된다")
	void assertNotRevoked_TokenIssuedBeforeRevocation_ThrowsException() {
		// 1. Given
		Claims claims = claimsIssuedAt(1_000_000L);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(REVOKED_BEFORE_KEY)).thenReturn("2000000");

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> jwtUtil.assertNotRevoked(claims));

		// 3. Then
		assertEquals(ErrorStatus.JWT_ACCESS_TOKEN_REVOKED, exception.getCode());
	}

	@Test
	@DisplayName("무효화 시점 이후 발급된 Access 토큰은 assertNotRevoked를 통과한다")
	void assertNotRevoked_TokenIssuedAfterRevocation_Passes() {
		// 1. Given
		Claims claims = claimsIssuedAt(3_000_000L);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(REVOKED_BEFORE_KEY)).thenReturn("2000000");

		// 2. When & Then
		assertDoesNotThrow(() -> jwtUtil.assertNotRevoked(claims));
	}

	@Test
	@DisplayName("무효화된 적 없는 회원의 토큰은 assertNotRevoked를 통과한다")
	void assertNotRevoked_NeverRevoked_Passes() {
		// 1. Given
		Claims claims = claimsWithMemberId();
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(REVOKED_BEFORE_KEY)).thenReturn(null);

		// 2. When & Then
		assertDoesNotThrow(() -> jwtUtil.assertNotRevoked(claims));
	}

	@Test
	@DisplayName("Redis 장애 시 assertNotRevoked는 fail-open으로 정상 요청을 통과시킨다")
	void assertNotRevoked_RedisFailure_FailsOpen() {
		// 1. Given
		Claims claims = mock(Claims.class);
		when(claims.get("userId")).thenReturn(MEMBER_ID.intValue());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(REVOKED_BEFORE_KEY))
			.thenThrow(new RedisConnectionFailureException("Redis 연결 실패"));

		// 2. When & Then
		assertDoesNotThrow(() -> jwtUtil.assertNotRevoked(claims));
	}
}
