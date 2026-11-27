package com.assu.server.domain.auth.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// CI(GitHub Actions가 CI 환경 변수를 설정)에서는 Docker가 없으면 건너뛰지 않고 실패시켜, 테스트가 조용히 빠지지 않게 한다
@Testcontainers
@EnabledIf("dockerAvailableOrCi")
class JwtUtilRedisIntegrationTest {

	static boolean dockerAvailableOrCi() {
		return System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable();
	}

	@Container
	private static final GenericContainer<?> REDIS =
		new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

	private static final Long MEMBER_ID = 7L;
	private static final Long OTHER_MEMBER_ID = 8L;

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;

	private MemberRepository memberRepository;
	private JwtUtil jwtUtil;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
	}

	@AfterAll
	static void disconnect() {
		connectionFactory.destroy();
	}

	@BeforeEach
	void setUp() {
		redisTemplate.execute(connection -> {
			connection.serverCommands().flushAll();
			return null;
		}, true);
		memberRepository = mock(MemberRepository.class);
		Member member = mock(Member.class);
		when(member.isWithdrawn()).thenReturn(false);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
		jwtUtil = new JwtUtil(memberRepository, redisTemplate, new SimpleMeterRegistry());
		jwtUtil.secretKey = "integration-test-secret-key-for-jwt-util-1234567890";
		ReflectionTestUtils.setField(jwtUtil, "accessValidSeconds", 3600);
		ReflectionTestUtils.setField(jwtUtil, "backofficeAccessValidSeconds", 1800);
		ReflectionTestUtils.setField(jwtUtil, "refreshValidSeconds", 1209600);
	}

	private TokensDTO issue() {
		return jwtUtil.issueTokens(MEMBER_ID, "user@assu.com", UserRole.PARTNER, "COMMON");
	}

	private String jti(String token) {
		Claims claims = jwtUtil.validateTokenOnlySignature(token);
		return claims.getId();
	}

	private Set<String> keys(String pattern) {
		Set<String> keys = new HashSet<>();
		try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
			cursor.forEachRemaining(keys::add);
		}
		return keys;
	}

	@Test
	@DisplayName("회전하면 기존 RT 키는 사라지고 새 RT 키와 3초짜리 유예 기록이 생긴다")
	void rotate_whenValid_thenReplacesRefreshKeyAndStoresGrace() {
		// given
		TokensDTO issued = issue();
		String oldJti = jti(issued.refreshToken());

		// when
		TokensDTO rotated = jwtUtil.rotateRefreshToken(issued.refreshToken());

		// then
		assertNotEquals(issued.refreshToken(), rotated.refreshToken());
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey("refresh:" + MEMBER_ID + ":" + oldJti)));
		assertEquals(rotated.refreshToken(),
			redisTemplate.opsForValue().get("refresh:" + MEMBER_ID + ":" + jti(rotated.refreshToken())));
		Long graceTtl = redisTemplate.getExpire("refresh-grace:" + MEMBER_ID + ":" + oldJti);
		assertTrue(graceTtl != null && graceTtl > 0 && graceTtl <= 3, "graceTtl=" + graceTtl);
	}

	@Test
	@DisplayName("유예 기간 안에 같은 RT로 다시 요청하면 새로 회전하지 않고 방금 발급한 토큰을 그대로 돌려준다")
	void rotate_whenRetriedWithinGrace_thenReturnsSameTokens() {
		// given
		TokensDTO issued = issue();
		TokensDTO first = jwtUtil.rotateRefreshToken(issued.refreshToken());

		// when
		TokensDTO retried = jwtUtil.rotateRefreshToken(issued.refreshToken());

		// then
		assertEquals(first, retried);
		assertEquals(1, keys("refresh:" + MEMBER_ID + ":*").size());
	}

	@Test
	@DisplayName("유예 기간이 지난 뒤 같은 RT로 다시 요청하면 REFRESH_TOKEN_NOT_EQUAL로 거절한다")
	void rotate_whenRetriedAfterGrace_thenRejects() {
		// given
		TokensDTO issued = issue();
		jwtUtil.rotateRefreshToken(issued.refreshToken());
		redisTemplate.delete("refresh-grace:" + MEMBER_ID + ":" + jti(issued.refreshToken()));

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> jwtUtil.rotateRefreshToken(issued.refreshToken()));

		// then
		assertEquals(ErrorStatus.REFRESH_TOKEN_NOT_EQUAL, exception.getCode());
	}

	@Test
	@DisplayName("저장된 RT도 유예 기록도 없으면 기다리지 않고 바로 거절한다")
	void rotate_whenRefreshKeyAndGraceMissing_thenRejectsWithoutWaiting() {
		// given
		TokensDTO issued = issue();
		redisTemplate.delete("refresh:" + MEMBER_ID + ":" + jti(issued.refreshToken()));

		// when
		long startedAt = System.nanoTime();
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> jwtUtil.rotateRefreshToken(issued.refreshToken()));
		long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

		// then
		assertEquals(ErrorStatus.REFRESH_TOKEN_NOT_EQUAL, exception.getCode());
		assertTrue(elapsedMillis < 200, "elapsedMillis=" + elapsedMillis);
	}

	@Test
	@DisplayName("유예 기간 중 로그아웃으로 RT를 모두 지우면 같은 RT 재시도를 거절한다")
	void rotate_whenLoggedOutDuringGrace_thenRejects() {
		// given
		TokensDTO issued = issue();
		jwtUtil.rotateRefreshToken(issued.refreshToken());
		jwtUtil.removeAllRefreshTokens(MEMBER_ID);

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> jwtUtil.rotateRefreshToken(issued.refreshToken()));

		// then
		assertEquals(ErrorStatus.REFRESH_TOKEN_NOT_EQUAL, exception.getCode());
	}

	@Test
	@DisplayName("저장된 값과 다른 RT로 요청하면 거절하고 저장된 RT는 지우지 않는다")
	void rotate_whenStoredValueDiffers_thenRejectsWithoutConsuming() {
		// given
		TokensDTO issued = issue();
		String refreshKey = "refresh:" + MEMBER_ID + ":" + jti(issued.refreshToken());
		redisTemplate.opsForValue().set(refreshKey, "other-token");

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> jwtUtil.rotateRefreshToken(issued.refreshToken()));

		// then
		assertEquals(ErrorStatus.REFRESH_TOKEN_NOT_EQUAL, exception.getCode());
		assertEquals("other-token", redisTemplate.opsForValue().get(refreshKey));
	}

	@Test
	@DisplayName("같은 RT로 동시에 20번 요청해도 회전은 한 번만 일어나고 모든 요청이 같은 토큰을 받는다")
	void rotate_whenConcurrentRequests_thenRotatesOnceAndAllGetSameTokens() throws Exception {
		// given
		TokensDTO issued = issue();
		int requests = 20;
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<TokensDTO>> results = new ArrayList<>();
		for (int i = 0; i < requests; i++) {
			Callable<TokensDTO> task = () -> {
				start.await();
				return jwtUtil.rotateRefreshToken(issued.refreshToken());
			};
			results.add(executor.submit(task));
		}

		// when
		start.countDown();
		Set<TokensDTO> distinct = new HashSet<>();
		for (Future<TokensDTO> result : results) {
			distinct.add(result.get());
		}
		executor.shutdown();

		// then
		assertEquals(1, distinct.size());
		assertEquals(1, keys("refresh:" + MEMBER_ID + ":*").size());
	}

	@Test
	@DisplayName("탈퇴 회원의 RT는 회전하지 않고 Redis의 RT도 지우지 않는다")
	void rotate_whenWithdrawnMember_thenRejectsWithoutConsumingRefreshKey() {
		// given
		TokensDTO issued = issue();
		Member withdrawn = mock(Member.class);
		when(withdrawn.isWithdrawn()).thenReturn(true);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(withdrawn));

		// when
		assertThrows(CustomAuthException.class, () -> jwtUtil.rotateRefreshToken(issued.refreshToken()));

		// then
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("refresh:" + MEMBER_ID + ":" + jti(issued.refreshToken()))));
	}

	@Test
	@DisplayName("전체 로그아웃은 SCAN으로 그 회원의 RT와 유예 기록만 모두 지우고 다른 회원과 다른 키는 남긴다")
	void removeAllRefreshTokens_whenManyKeys_thenDeletesOnlyThatMembersKeys() {
		// given
		for (int i = 0; i < 250; i++) {
			redisTemplate.opsForValue().set("refresh:" + MEMBER_ID + ":jti-" + i, "rt", Duration.ofMinutes(5));
		}
		redisTemplate.opsForValue().set("refresh-grace:" + MEMBER_ID + ":jti-0", "a r", Duration.ofSeconds(10));
		redisTemplate.opsForValue().set("refresh:" + OTHER_MEMBER_ID + ":jti-x", "rt", Duration.ofMinutes(5));
		redisTemplate.opsForValue().set("refresh-grace:" + OTHER_MEMBER_ID + ":jti-x", "a r", Duration.ofSeconds(10));
		redisTemplate.opsForValue().set("refresh:" + MEMBER_ID + "0:jti-y", "rt", Duration.ofMinutes(5));
		redisTemplate.opsForValue().set("blacklist:some-jti", "1", Duration.ofMinutes(5));

		// when
		jwtUtil.removeAllRefreshTokens(MEMBER_ID);

		// then
		assertTrue(keys("refresh:" + MEMBER_ID + ":*").isEmpty());
		assertTrue(keys("refresh-grace:" + MEMBER_ID + ":*").isEmpty());
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("refresh:" + OTHER_MEMBER_ID + ":jti-x")));
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("refresh-grace:" + OTHER_MEMBER_ID + ":jti-x")));
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("refresh:" + MEMBER_ID + "0:jti-y")));
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:some-jti")));
	}
}
