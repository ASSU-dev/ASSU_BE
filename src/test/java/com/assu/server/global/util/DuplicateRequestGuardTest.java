package com.assu.server.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.support.InMemoryDuplicateRequestGuard;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class DuplicateRequestGuardTest {

	private static final Duration TTL = Duration.ofSeconds(10);

	private final Map<String, String> store = new ConcurrentHashMap<>();
	private final DuplicateRequestGuard guard = InMemoryDuplicateRequestGuard.create(store);

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void acquire_whenSameRequestWithinTtl_thenThrowsDuplicateRequest() {
		// given
		guard.acquire("inquiry-create", 1L, TTL, "제목", "내용");

		// when & then
		assertThatThrownBy(() -> guard.acquire("inquiry-create", 1L, TTL, "제목", "내용"))
			.isInstanceOf(GeneralException.class)
			.extracting("code")
			.isEqualTo(ErrorStatus.DUPLICATE_REQUEST);
	}

	@Test
	void acquire_whenOnlySurroundingWhitespaceDiffers_thenTreatsAsDuplicate() {
		// given
		guard.acquire("inquiry-create", 1L, TTL, "제목", "내용");

		// when & then
		assertThatThrownBy(() -> guard.acquire("inquiry-create", 1L, TTL, "  제목 ", "내용\n"))
			.isInstanceOf(GeneralException.class);
	}

	@Test
	void acquire_whenContentMemberOrActionDiffers_thenAllowed() {
		// given
		guard.acquire("inquiry-create", 1L, TTL, "제목", "내용");

		// when & then
		assertThatCode(() -> {
			guard.acquire("inquiry-create", 1L, TTL, "제목", "다른 내용");
			guard.acquire("inquiry-create", 2L, TTL, "제목", "내용");
			guard.acquire("suggestion-create", 1L, TTL, "제목", "내용");
		}).doesNotThrowAnyException();
		assertThat(store).hasSize(4);
	}

	@Test
	void acquire_whenValuesShiftAcrossBoundary_thenAllowed() {
		// given
		guard.acquire("inquiry-create", 1L, TTL, "ab", "c");

		// when & then
		assertThatCode(() -> guard.acquire("inquiry-create", 1L, TTL, "a", "bc")).doesNotThrowAnyException();
		assertThatCode(() -> guard.acquire("inquiry-create", 1L, TTL, null, "c")).doesNotThrowAnyException();
		assertThatCode(() -> guard.acquire("inquiry-create", 1L, TTL, "null", "c")).doesNotThrowAnyException();
	}

	@Test
	void acquire_whenTransactionRolledBack_thenReleasesKeyForImmediateRetry() {
		// given
		TransactionSynchronizationManager.initSynchronization();
		guard.acquire("app-review-create", 1L, TTL, 5, "좋아요");

		// when
		complete(TransactionSynchronization.STATUS_ROLLED_BACK);

		// then
		assertThat(store).isEmpty();
		assertThatCode(() -> guard.acquire("app-review-create", 1L, TTL, 5, "좋아요")).doesNotThrowAnyException();
	}

	@Test
	void acquire_whenTransactionCommitted_thenKeepsKey() {
		// given
		TransactionSynchronizationManager.initSynchronization();
		guard.acquire("app-review-create", 1L, TTL, 5, "좋아요");

		// when
		complete(TransactionSynchronization.STATUS_COMMITTED);

		// then
		assertThat(store).hasSize(1);
		assertThatThrownBy(() -> guard.acquire("app-review-create", 1L, TTL, 5, "좋아요"))
			.isInstanceOf(GeneralException.class);
	}

	@Test
	void acquire_whenRolledBackAfterKeyTakenByAnotherRequest_thenDoesNotDeleteOthersKey() {
		// given
		TransactionSynchronizationManager.initSynchronization();
		guard.acquire("app-review-create", 1L, TTL, 5, "좋아요");
		String key = store.keySet().iterator().next();
		store.put(key, "other-request-token");

		// when
		complete(TransactionSynchronization.STATUS_ROLLED_BACK);

		// then
		assertThat(store).containsEntry(key, "other-request-token");
	}

	@Test
	void acquire_whenDuplicateRejected_thenDoesNotReleaseOriginalKeyOnRollback() {
		// given
		guard.acquire("app-review-create", 1L, TTL, 5, "좋아요");
		TransactionSynchronizationManager.initSynchronization();

		// when
		assertThatThrownBy(() -> guard.acquire("app-review-create", 1L, TTL, 5, "좋아요"))
			.isInstanceOf(GeneralException.class);
		complete(TransactionSynchronization.STATUS_ROLLED_BACK);

		// then
		assertThat(store).hasSize(1);
	}

	@Test
	@SuppressWarnings("unchecked")
	void acquire_whenRedisUnavailable_thenFailsOpenAndCountsMetric() {
		// given
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
			.thenThrow(new RedisConnectionFailureException("down"));
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		DuplicateRequestGuard failingGuard = new DuplicateRequestGuard(redisTemplate, meterRegistry);

		// when & then
		assertThatCode(() -> {
			failingGuard.acquire("backoffice-push", 1L, TTL, "공지");
			failingGuard.acquire("backoffice-push", 1L, TTL, "공지");
		}).doesNotThrowAnyException();
		assertThat(meterRegistry.counter("request.duplicate.failopen", "action", "backoffice-push").count())
			.isEqualTo(2.0);
	}

	private void complete(int status) {
		List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
		if (status == TransactionSynchronization.STATUS_COMMITTED) {
			synchronizations.forEach(TransactionSynchronization::afterCommit);
		}
		synchronizations.forEach(s -> s.afterCompletion(status));
	}
}
