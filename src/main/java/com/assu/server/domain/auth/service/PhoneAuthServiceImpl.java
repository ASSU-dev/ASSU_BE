package com.assu.server.domain.auth.service;

import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.util.RandomNumberUtil;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.infra.aligo.client.AligoSmsClient;
import com.assu.server.infra.aligo.dto.AligoSendResponse;
import com.assu.server.infra.aligo.exception.AligoException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PhoneAuthServiceImpl implements PhoneAuthService {

    private final StringRedisTemplate redisTemplate;
    private final AligoSmsClient aligoSmsClient;
    private final PartnerRepository partnerRepository;
    private final AdminRepository adminRepository;
    private final MeterRegistry meterRegistry;

    private static final Duration AUTH_CODE_TTL = Duration.ofMinutes(5); // 인증번호 5분 유효
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30); // 인증 완료 후 가입까지 허용 시간
    private static final String VERIFIED_KEY_PREFIX = "phone-verified:";
    private static final int MAX_VERIFY_FAILURES = 5;
    private static final String VERIFY_FAILURE_KEY_PREFIX = "phone-verify-fail:";
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final String SEND_COOLDOWN_KEY_PREFIX = "phone-send-cooldown:";
    private static final int MAX_SENDS_PER_WINDOW = 10;
    private static final Duration SEND_COUNT_WINDOW = Duration.ofHours(24);
    private static final String SEND_COUNT_KEY_PREFIX = "phone-send-count:";

    // INCR 뒤 EXPIRE 전에 종료되면 만료 없는 카운터가 남으므로 한 스크립트로 묶는다
    private static final RedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    // iOS 앱스토어 심사(해외 번호)용 마스터 번호 - 알리고가 국내 번호만 지원해 SMS 수신이 불가능한 심사 환경 우회용
    private static final String MASTER_PHONE_NUMBER = "01000000000";
    private static final String MASTER_AUTH_NUMBER = "123456";

    @Value("${phone-auth.master-key.enabled:false}")
    private boolean masterKeyEnabled;

    @Override
    @Transactional(readOnly = true)
    public void checkAndSendAuthNumber(String phoneNumber) {
        if (isMasterPhoneNumber(phoneNumber)) {
            return;
        }
        assertPhoneNotInUse(phoneNumber);

        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        String cooldownKey = SEND_COOLDOWN_KEY_PREFIX + phoneNumber;
        String sendCountKey = SEND_COUNT_KEY_PREFIX + phoneNumber;

        // SET NX로 선점하므로 동시 요청 중 하나만 발송까지 진행한다
        if (!Boolean.TRUE.equals(valueOps.setIfAbsent(cooldownKey, "1", SEND_COOLDOWN))) {
            rejectSend("cooldown", ErrorStatus.PHONE_AUTH_SEND_COOLDOWN);
        }

        String sendCount = valueOps.get(sendCountKey);
        if (sendCount != null && Long.parseLong(sendCount) >= MAX_SENDS_PER_WINDOW) {
            rejectSend("send-limit", ErrorStatus.PHONE_AUTH_SEND_LIMIT_EXCEEDED);
        }

        String authNumber = RandomNumberUtil.generateSixDigit();
        valueOps.set(phoneNumber, authNumber, AUTH_CODE_TTL);
        redisTemplate.delete(VERIFY_FAILURE_KEY_PREFIX + phoneNumber);

        String message = "[ASSU] 인증번호: " + authNumber;

        AligoSendResponse response;
        try {
            response = aligoSmsClient.sendSms(phoneNumber, message, "사용자");
        } catch (AligoException e) {
            discardFailedSend(phoneNumber);
            throw e;
        }

        // 실패 처리
        if (!"1".equals(response.getResult_code())) {
            discardFailedSend(phoneNumber);
            throw new CustomAuthException(ErrorStatus.FAILED_TO_SEND_SMS);
        }

        // 실제로 발송된 건만 세어, 발송 실패가 사용자의 발송 한도를 깎지 않게 한다
        incrementWithTtl(sendCountKey, SEND_COUNT_WINDOW);
    }

    private Long incrementWithTtl(String key, Duration ttl) {
        return redisTemplate.execute(INCREMENT_WITH_TTL_SCRIPT, List.of(key), String.valueOf(ttl.toSeconds()));
    }

    private void rejectSend(String reason, ErrorStatus errorStatus) {
        meterRegistry.counter("auth.phone.blocked", "reason", reason).increment();
        throw new CustomAuthException(errorStatus);
    }

    private void discardFailedSend(String phoneNumber) {
        redisTemplate.delete(phoneNumber);
        redisTemplate.delete(SEND_COOLDOWN_KEY_PREFIX + phoneNumber);
    }

    @Override
    public void verifyAuthNumber(String phoneNumber, String authNumber) {
        if (isMasterPhoneNumber(phoneNumber)) {
            if (!MASTER_AUTH_NUMBER.equals(authNumber)) {
                throw new CustomAuthException(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER);
            }

            redisTemplate.opsForValue().set(VERIFIED_KEY_PREFIX + phoneNumber, "1", VERIFIED_TTL);
            return;
        }

        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        String stored = valueOps.get(phoneNumber);

        if (stored == null) {
            throw new CustomAuthException(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER);
        }

        String failureKey = VERIFY_FAILURE_KEY_PREFIX + phoneNumber;
        if (!stored.equals(authNumber)) {
            Long failures = incrementWithTtl(failureKey, AUTH_CODE_TTL);
            if (failures != null && failures >= MAX_VERIFY_FAILURES) {
                redisTemplate.delete(phoneNumber);
                redisTemplate.delete(failureKey);
                meterRegistry.counter("auth.phone.blocked", "reason", "verify-attempts").increment();
                throw new CustomAuthException(ErrorStatus.PHONE_AUTH_ATTEMPTS_EXCEEDED);
            }
            throw new CustomAuthException(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER);
        }

        redisTemplate.delete(phoneNumber);
        redisTemplate.delete(failureKey);

        // 가입 요청이 인증을 실제로 거쳤는지 확인할 수 있도록 인증 완료 표식을 남긴다
        valueOps.set(VERIFIED_KEY_PREFIX + phoneNumber, "1", VERIFIED_TTL);
    }

    @Override
    public void consumeVerification(String phoneNumber) {
        Boolean verified = redisTemplate.delete(VERIFIED_KEY_PREFIX + phoneNumber);

        if (!Boolean.TRUE.equals(verified)) {
            throw new CustomAuthException(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER);
        }
    }

    @Override
    public void assertPhoneNotInUse(String phoneNumber) {
        if (isMasterPhoneNumber(phoneNumber)) {
            return;
        }
        // 탈퇴 회원의 번호는 재가입 대상이므로 중복으로 보지 않는다
        if (partnerRepository.existsByPhoneNumAndMember_DeletedAtIsNull(phoneNumber)
                || adminRepository.existsByPhoneNumAndMember_DeletedAtIsNull(phoneNumber)) {
            throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
        }
    }

    @Override
    public boolean isMasterPhoneNumber(String phoneNumber) {
        return masterKeyEnabled && MASTER_PHONE_NUMBER.equals(phoneNumber);
    }
}
