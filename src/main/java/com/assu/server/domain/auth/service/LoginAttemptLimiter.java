package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    public enum Policy {
        COMMON("common", 5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
        BACKOFFICE("backoffice", 5, Duration.ofMinutes(15), Duration.ofMinutes(30));

        private final String realm;
        private final int maxFailures;
        private final Duration failureWindow;
        private final Duration blockDuration;

        Policy(String realm, int maxFailures, Duration failureWindow, Duration blockDuration) {
            this.realm = realm;
            this.maxFailures = maxFailures;
            this.failureWindow = failureWindow;
            this.blockDuration = blockDuration;
        }
    }

    private static final String KEY_PREFIX = "login-fail:";

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    // 확인 후 증가 방식은 동시 요청이 한도를 넘겨 통과하므로, 시도를 먼저 세고 인증에 성공하면 reset으로 지운다
    public void checkAttempt(Policy policy, String identifier) {
        if (!exceedsLimit(policy, identifier)) {
            return;
        }
        meterRegistry.counter("auth.login.blocked", "realm", policy.realm).increment();
        log.warn("[LOGIN_BLOCKED] realm={}", policy.realm);
        throw new CustomAuthException(ErrorStatus.LOGIN_ATTEMPTS_EXCEEDED);
    }

    public void reset(Policy policy, String identifier) {
        try {
            redisTemplate.delete(key(policy, identifier));
        } catch (RuntimeException exception) {
            failOpen(policy, exception);
        }
    }

    private boolean exceedsLimit(Policy policy, String identifier) {
        String key = key(policy, identifier);
        try {
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts == null) {
                return false;
            }
            if (attempts == policy.maxFailures) {
                redisTemplate.expire(key, policy.blockDuration);
            } else if (attempts == 1) {
                redisTemplate.expire(key, policy.failureWindow);
            }
            if (attempts <= policy.maxFailures) {
                return false;
            }
            // INCR 직후 EXPIRE가 유실되어 TTL이 없는 키는 영구 차단이 되므로 여기서 복구한다
            Long ttlSeconds = redisTemplate.getExpire(key);
            if (ttlSeconds != null && ttlSeconds == -1) {
                redisTemplate.expire(key, policy.blockDuration);
            }
            return true;
        } catch (RuntimeException exception) {
            failOpen(policy, exception);
            return false;
        }
    }

    // Redis 장애가 전체 로그인 차단으로 번지지 않도록 제한 없이 통과시킨다(fail-open)
    private void failOpen(Policy policy, RuntimeException exception) {
        log.warn("Redis 로그인 시도 제한 처리 실패로 fail-open 처리. realm={}", policy.realm, exception);
        meterRegistry.counter("auth.login.limit.failopen").increment();
    }

    private String key(Policy policy, String identifier) {
        return KEY_PREFIX + policy.realm + ":" + identifier.trim().toLowerCase(Locale.ROOT);
    }
}
