package com.assu.server.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// CI(GitHub Actions가 CI 환경 변수를 설정)에서는 Docker가 없으면 건너뛰지 않고 실패시켜, 테스트가 조용히 빠지지 않게 한다
@Testcontainers
@EnabledIf("dockerAvailableOrCi")
class DuplicateRequestGuardRedisIntegrationTest {

	static boolean dockerAvailableOrCi() {
		return System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable();
	}

	@Container
	private static final GenericContainer<?> REDIS =
		new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

	private static final Duration TTL = Duration.ofSeconds(10);

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;

	private SimpleMeterRegistry meterRegistry;
	private DuplicateRequestGuard guard;

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
		meterRegistry = new SimpleMeterRegistry();
		guard = new DuplicateRequestGuard(redisTemplate, meterRegistry);
	}

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void acquire_whenFirstRequest_thenStoresHashedKeyWithTtl() {
		// when
		guard.acquire("inquiry-create", 7L, TTL, "제목", "내용", "user@assu.com");

		// then
		Set<String> keys = redisTemplate.keys("dup:*");
		assertThat(keys).hasSize(1);
		String key = keys.iterator().next();
		assertThat(key).matches("dup:inquiry-create:7:[0-9a-f]{64}");
		Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
		assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(TTL.toMillis());
	}

	@Test
	void acquire_whenSameRequestRepeated_thenRejectsAndCountsBlocked() {
		// given
		guard.acquire("suggestion-create", 7L, TTL, 1L, "스타벅스", "10% 할인");

		// when & then
		assertThatThrownBy(() -> guard.acquire("suggestion-create", 7L, TTL, 1L, "스타벅스", "10% 할인"))
			.isInstanceOf(GeneralException.class)
			.extracting("code")
			.isEqualTo(ErrorStatus.DUPLICATE_REQUEST);
		assertThat(meterRegistry.counter("request.duplicate.blocked", "action", "suggestion-create").count())
			.isEqualTo(1.0);
	}

	@Test
	void acquire_whenTransactionRolledBack_thenReleasesKeyWithLuaScript() {
		// given
		TransactionSynchronizationManager.initSynchronization();
		guard.acquire("partnership-draft", 7L, TTL, 20L);
		assertThat(redisTemplate.keys("dup:*")).hasSize(1);

		// when
		TransactionSynchronizationManager.getSynchronizations()
			.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		TransactionSynchronizationManager.clearSynchronization();

		// then
		assertThat(redisTemplate.keys("dup:*")).isEmpty();
		assertThatCode(() -> guard.acquire("partnership-draft", 7L, TTL, 20L)).doesNotThrowAnyException();
	}

	@Test
	void acquire_whenRolledBackAfterAnotherRequestOwnsKey_thenKeepsOthersKey() {
		// given
		TransactionSynchronizationManager.initSynchronization();
		guard.acquire("partnership-draft", 7L, TTL, 20L);
		String key = redisTemplate.keys("dup:*").iterator().next();
		redisTemplate.opsForValue().set(key, "other-request-token", TTL);

		// when
		TransactionSynchronizationManager.getSynchronizations()
			.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		// then
		assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("other-request-token");
	}

	@Test
	void acquire_whenConcurrentIdenticalRequests_thenOnlyOneSucceeds() throws Exception {
		// given
		int threads = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		Callable<Boolean> task = () -> {
			start.await();
			try {
				guard.acquire("backoffice-push", 1L, TTL, "ALL", "공지", "본문");
				return true;
			} catch (GeneralException exception) {
				return false;
			}
		};

		// when
		for (int i = 0; i < threads; i++) {
			results.add(executor.submit(task));
		}
		start.countDown();
		int succeeded = 0;
		for (Future<Boolean> result : results) {
			if (result.get(10, TimeUnit.SECONDS)) {
				succeeded++;
			}
		}
		executor.shutdown();

		// then
		assertThat(succeeded).isEqualTo(1);
	}
}
