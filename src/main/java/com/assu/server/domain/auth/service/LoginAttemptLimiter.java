package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.util.LogMaskUtil;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    enum Policy {
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

    // INCR와 EXPIRE가 따로 실행되면 늦게 도착한 EXPIRE가 차단 시간을 덮어쓰므로 한 스크립트로 묶는다
    private static final RedisScript<Long> COUNT_ATTEMPT_SCRIPT = RedisScript.of("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            if attempts == tonumber(ARGV[2]) then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
            return attempts
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final CommonAuthRepository commonAuthRepository;

    // 확인 후 증가 방식은 동시 요청이 한도를 넘겨 통과하므로, 시도를 먼저 세고 인증에 성공하면 reset으로 지운다
    public void checkAttempt(String identifier) {
        String account = normalize(identifier);
        Policy policy = resolvePolicy(account);
        if (!exceedsLimit(policy, account)) {
            return;
        }
        meterRegistry.counter("auth.login.blocked", "realm", policy.realm).increment();
        log.warn("[LOGIN_BLOCKED] realm={} account={}", policy.realm, LogMaskUtil.mask(identifier));
        throw new CustomAuthException(ErrorStatus.LOGIN_ATTEMPTS_EXCEEDED);
    }

    public void reset(String identifier) {
        try {
            redisTemplate.delete(KEY_PREFIX + normalize(identifier));
        } catch (RuntimeException exception) {
            log.warn("Redis 로그인 시도 횟수 초기화 실패", exception);
            meterRegistry.counter("auth.login.limit.failopen").increment();
        }
    }

    // 차단 시간을 요청 경로가 아니라 계정 역할로 정해, 공통 로그인 경로로 백오피스 계정을 대입해도 30분 차단이 적용되게 한다
    // 카운터 키와 같은 정규화 값으로 조회한다. email 컬럼은 대소문자를 구분하지 않는 콜레이션이라 저장된 표기와 무관하게 찾는다
    private Policy resolvePolicy(String account) {
        return commonAuthRepository.findRoleByEmail(account)
                .filter(role -> role == UserRole.BACKOFFICE)
                .map(role -> Policy.BACKOFFICE)
                .orElse(Policy.COMMON);
    }

    private boolean exceedsLimit(Policy policy, String account) {
        try {
            Long attempts = redisTemplate.execute(COUNT_ATTEMPT_SCRIPT, List.of(KEY_PREFIX + account),
                    String.valueOf(policy.failureWindow.toSeconds()),
                    String.valueOf(policy.maxFailures),
                    String.valueOf(policy.blockDuration.toSeconds()));
            return attempts != null && attempts > policy.maxFailures;
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

    // 로그인 경로나 이메일 표기를 바꿔 한도를 늘릴 수 없도록 계정 하나에 카운터 하나만 둔다
    private static String normalize(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }
}
