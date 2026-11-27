package com.assu.server.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

class StoreRegistrationLockTest {

    private static final Duration TTL = Duration.ofSeconds(30);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private StoreRegistrationLock lock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lock = new StoreRegistrationLock(redisTemplate, TTL, Duration.ofMillis(100), Duration.ofMillis(10));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void acquire_whenLockHeldByOtherRequest_thenThrowsInProgressWithoutRegisteringRelease() {
        // given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> lock.acquire("가게", "서울특별시 동작구 상도로 369", "2층"))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.STORE_REGISTRATION_IN_PROGRESS);
        verify(valueOperations, atLeast(2)).setIfAbsent(anyString(), anyString(), eq(TTL));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void acquire_whenRedisReturnsNull_thenTreatedAsNotAcquired() {
        // given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> lock.acquire("가게", "주소", null))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.STORE_REGISTRATION_IN_PROGRESS);
    }

    @Test
    void acquire_whenLockFreedWithinWait_thenAcquiresAfterRetry() {
        // given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false, false, true);

        // when
        lock.acquire("가게", "주소", "2층");

        // then
        verify(valueOperations, times(3)).setIfAbsent(anyString(), anyString(), eq(TTL));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acquire_whenTransactionCompletes_thenReleasesWithOwnToken() {
        // given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lock.acquire("가게", "주소", "2층");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(key.capture(), token.capture(), eq(TTL));
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());

        // when
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        // then
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(key.getValue())), eq(token.getValue()));
    }

    @Test
    void acquire_whenNoActiveTransaction_thenThrowsWithoutTouchingRedis() {
        // given
        TransactionSynchronizationManager.clearSynchronization();

        // when / then
        assertThatThrownBy(() -> lock.acquire("가게", "주소", "2층"))
                .isInstanceOf(IllegalStateException.class);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void lockKey_whenOnlyCaseWhitespaceOrNullDiffers_thenSameKey() {
        // when
        String base = StoreRegistrationLock.lockKey("Cafe", "서울 동작구", null);

        // then
        assertThat(StoreRegistrationLock.lockKey(" cafe ", "서울 동작구 ", "")).isEqualTo(base);
        assertThat(StoreRegistrationLock.lockKey("Cafe", "서울 동작구", "2층")).isNotEqualTo(base);
        assertThat(StoreRegistrationLock.lockKey("Cafe2", "서울 동작구", null)).isNotEqualTo(base);
    }
}
