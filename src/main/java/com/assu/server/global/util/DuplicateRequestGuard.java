package com.assu.server.global.util;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DuplicateRequestGuard {

    public static final Duration DEFAULT_TTL = Duration.ofSeconds(10);

    private static final String KEY_PREFIX = "dup:";
    private static final String NULL_MARKER = "\u0000";

    // TTL이 지나 다른 요청이 같은 키를 잡은 뒤에 늦은 롤백이 그 키를 지우지 않도록 자기 토큰일 때만 삭제한다
    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    // 트랜잭션 안에서 호출해야 작업이 롤백될 때 키가 풀려 즉시 재시도할 수 있다
    public void acquire(String action, Long memberId, Duration ttl, Object... contents) {
        String key = KEY_PREFIX + action + ":" + memberId + ":" + hash(contents);
        String token = UUID.randomUUID().toString();

        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        } catch (RuntimeException exception) {
            log.warn("Redis 중복 요청 확인 실패로 fail-open 처리. action={}", action, exception);
            meterRegistry.counter("request.duplicate.failopen", "action", action).increment();
            return;
        }

        if (Boolean.FALSE.equals(acquired)) {
            meterRegistry.counter("request.duplicate.blocked", "action", action).increment();
            log.info("[DUPLICATE_REQUEST] action={} memberId={}", action, memberId);
            throw new GeneralException(ErrorStatus.DUPLICATE_REQUEST);
        }

        TransactionCallbacks.runAfterRollback(() -> release(key, token, action));
    }

    private void release(String key, String token, String action) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        } catch (RuntimeException exception) {
            log.warn("Redis 중복 요청 키 해제 실패. action={}", action, exception);
            meterRegistry.counter("request.duplicate.release.failed", "action", action).increment();
        }
    }

    // 구분자를 넣어도 값 안의 구분자와 충돌할 수 있어 각 값 앞에 길이를 붙여 경계를 고정한다
    private static String hash(Object... contents) {
        StringBuilder normalized = new StringBuilder();
        for (Object content : contents) {
            String value = normalize(content);
            normalized.append(value.length()).append(':').append(value).append('|');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private static String normalize(Object content) {
        if (content == null) {
            return NULL_MARKER;
        }
        if (content instanceof String text) {
            return text.strip();
        }
        return String.valueOf(content);
    }
}
