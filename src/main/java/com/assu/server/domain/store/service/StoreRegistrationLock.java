package com.assu.server.domain.store.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

@Component
public class StoreRegistrationLock {

    static final String KEY_PREFIX = "lock:store-registration:";

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMillis(50);

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final Duration wait;
    private final Duration retryInterval;

    @Autowired
    public StoreRegistrationLock(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_TTL, DEFAULT_WAIT, DEFAULT_RETRY_INTERVAL);
    }

    StoreRegistrationLock(StringRedisTemplate redisTemplate, Duration ttl, Duration wait, Duration retryInterval) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
        this.wait = wait;
        this.retryInterval = retryInterval;
    }

    public void acquire(String name, String address, String detailAddress) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("StoreRegistrationLock은 트랜잭션 안에서만 사용할 수 있습니다.");
        }
        String key = lockKey(name, address, detailAddress);
        String token = UUID.randomUUID().toString();
        if (!tryAcquire(key, token)) {
            throw new GeneralException(ErrorStatus.STORE_REGISTRATION_IN_PROGRESS);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                release(key, token);
            }
        });
    }

    public static String lockKey(String name, String address, String detailAddress) {
        String raw = normalize(name) + '\u0000' + normalize(address) + '\u0000' + normalize(detailAddress);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // DB 조회보다 넓게(대소문자·앞뒤 공백·null/빈값 무시) 묶어야 MariaDB ci 콜레이션에서 같은 행으로 매칭되는 요청이 서로 다른 락을 잡지 않는다
    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private boolean tryAcquire(String key, String token) {
        long deadline = System.nanoTime() + wait.toNanos();
        while (true) {
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, ttl))) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(retryInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void release(String key, String token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
    }
}
