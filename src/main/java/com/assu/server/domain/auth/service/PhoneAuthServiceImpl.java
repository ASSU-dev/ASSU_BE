package com.assu.server.domain.auth.service;

import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.util.RandomNumberUtil;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.infra.aligo.client.AligoSmsClient;
import com.assu.server.infra.aligo.dto.AligoSendResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

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
    private static final String SEND_WINDOW_KEY_PREFIX = "phone-send-window:";
    private static final String RESERVED_PREFIX = "RESERVED:";

    // 한도 확인·쿨다운 선점·발송 횟수 예약·인증번호 저장을 한 스크립트로 묶어, 동시 요청이 같은 횟수를 보고 한도를 넘겨 발송하지 못하게 한다
    // 횟수 키를 새로 만들 때만 창 만료를 정하고 같은 만료로 집계 창 식별자를 둔다. 횟수가 0으로 돌아가도 창 만료는 바뀌지 않는다
    private static final RedisScript<String> RESERVE_SEND_SCRIPT = RedisScript.of("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then return 'LIMIT' end
            if not redis.call('SET', KEYS[2], ARGV[6], 'NX', 'EX', ARGV[2]) then return 'COOLDOWN' end
            if redis.call('EXISTS', KEYS[1]) == 0 then
              redis.call('SET', KEYS[1], '1', 'EX', ARGV[3])
              redis.call('SET', KEYS[5], ARGV[6], 'EX', ARGV[3])
            else
              redis.call('INCR', KEYS[1])
              if redis.call('EXISTS', KEYS[5]) == 0 then
                local ttl = redis.call('PTTL', KEYS[1])
                if ttl > 0 then redis.call('SET', KEYS[5], ARGV[6], 'PX', ttl)
                else redis.call('SET', KEYS[5], ARGV[6], 'EX', ARGV[3]) end
              end
            end
            redis.call('SET', KEYS[3], ARGV[4], 'EX', ARGV[5])
            redis.call('DEL', KEYS[4])
            return 'RESERVED:' .. redis.call('GET', KEYS[5])
            """, String.class);

    // 발송 실패 시 이 요청의 몫만 되돌린다. 쿨다운은 아직 이 요청의 것일 때만, 횟수는 예약한 집계 창이 아직 유지될 때만 되돌리고, 인증번호는 그사이 바뀌지 않았을 때만 지운다
    private static final RedisScript<Long> RELEASE_SEND_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[3]) == ARGV[1] then redis.call('DEL', KEYS[3]) end
            if redis.call('GET', KEYS[2]) == ARGV[2] then redis.call('DEL', KEYS[2]) end
            if redis.call('GET', KEYS[4]) == ARGV[3] and tonumber(redis.call('GET', KEYS[1]) or '0') > 0 then
              redis.call('DECR', KEYS[1])
            end
            return 1
            """, Long.class);

    // 조회·비교·실패 집계·폐기·완료 표식이 따로 실행되면 동시 요청이 모두 폐기 전 인증번호와 비교되므로 한 스크립트로 묶는다
    private static final RedisScript<String> VERIFY_SCRIPT = RedisScript.of("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then return 'MISSING' end
            if stored == ARGV[1] then
              redis.call('DEL', KEYS[1], KEYS[2])
              redis.call('SET', KEYS[3], '1', 'EX', ARGV[4])
              return 'VERIFIED'
            end
            local failures = redis.call('INCR', KEYS[2])
            if failures == 1 then redis.call('EXPIRE', KEYS[2], ARGV[3]) end
            if failures >= tonumber(ARGV[2]) then
              redis.call('DEL', KEYS[1], KEYS[2])
              return 'EXHAUSTED'
            end
            return 'MISMATCH'
            """, String.class);

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

        String authNumber = RandomNumberUtil.generateSixDigit();
        String reservationId = UUID.randomUUID().toString();
        String reservation = redisTemplate.execute(RESERVE_SEND_SCRIPT, sendKeys(phoneNumber),
                String.valueOf(MAX_SENDS_PER_WINDOW),
                String.valueOf(SEND_COOLDOWN.toSeconds()),
                String.valueOf(SEND_COUNT_WINDOW.toSeconds()),
                authNumber,
                String.valueOf(AUTH_CODE_TTL.toSeconds()),
                reservationId);

        if ("LIMIT".equals(reservation)) {
            rejectSend("send-limit", ErrorStatus.PHONE_AUTH_SEND_LIMIT_EXCEEDED);
        }
        if (reservation == null || !reservation.startsWith(RESERVED_PREFIX)) {
            rejectSend("cooldown", ErrorStatus.PHONE_AUTH_SEND_COOLDOWN);
        }
        String windowId = reservation.substring(RESERVED_PREFIX.length());

        String message = "[ASSU] 인증번호: " + authNumber;

        AligoSendResponse response;
        try {
            response = aligoSmsClient.sendSms(phoneNumber, message, "사용자");
        } catch (RuntimeException e) {
            releaseFailedSend(phoneNumber, authNumber, reservationId, windowId);
            throw e;
        }

        if (!"1".equals(response.getResult_code())) {
            releaseFailedSend(phoneNumber, authNumber, reservationId, windowId);
            throw new CustomAuthException(ErrorStatus.FAILED_TO_SEND_SMS);
        }
    }

    private List<String> sendKeys(String phoneNumber) {
        return List.of(SEND_COUNT_KEY_PREFIX + phoneNumber, SEND_COOLDOWN_KEY_PREFIX + phoneNumber,
                phoneNumber, VERIFY_FAILURE_KEY_PREFIX + phoneNumber, SEND_WINDOW_KEY_PREFIX + phoneNumber);
    }

    private List<String> releaseKeys(String phoneNumber) {
        return List.of(SEND_COUNT_KEY_PREFIX + phoneNumber, SEND_COOLDOWN_KEY_PREFIX + phoneNumber,
                phoneNumber, SEND_WINDOW_KEY_PREFIX + phoneNumber);
    }

    private void rejectSend(String reason, ErrorStatus errorStatus) {
        meterRegistry.counter("auth.phone.blocked", "reason", reason).increment();
        throw new CustomAuthException(errorStatus);
    }

    // 발송 실패가 사용자의 발송 한도와 쿨다운을 깎지 않도록 예약을 되돌린다
    private void releaseFailedSend(String phoneNumber, String authNumber, String reservationId, String windowId) {
        redisTemplate.execute(RELEASE_SEND_SCRIPT, releaseKeys(phoneNumber), authNumber, reservationId, windowId);
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

        String result = redisTemplate.execute(VERIFY_SCRIPT,
                List.of(phoneNumber, VERIFY_FAILURE_KEY_PREFIX + phoneNumber, VERIFIED_KEY_PREFIX + phoneNumber),
                authNumber,
                String.valueOf(MAX_VERIFY_FAILURES),
                String.valueOf(AUTH_CODE_TTL.toSeconds()),
                String.valueOf(VERIFIED_TTL.toSeconds()));

        if ("VERIFIED".equals(result)) {
            return;
        }
        if ("EXHAUSTED".equals(result)) {
            meterRegistry.counter("auth.phone.blocked", "reason", "verify-attempts").increment();
            throw new CustomAuthException(ErrorStatus.PHONE_AUTH_ATTEMPTS_EXCEEDED);
        }
        throw new CustomAuthException(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER);
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
