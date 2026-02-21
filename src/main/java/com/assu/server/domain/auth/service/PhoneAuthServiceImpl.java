package com.assu.server.domain.auth.service;

import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.util.RandomNumberUtil;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.infra.aligo.client.AligoSmsClient;
import com.assu.server.infra.aligo.dto.AligoSendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PhoneAuthServiceImpl implements PhoneAuthService {

    private final StringRedisTemplate redisTemplate;
    private final AligoSmsClient aligoSmsClient;
    private final MemberRepository memberRepository;

    private static final Duration AUTH_CODE_TTL = Duration.ofMinutes(5); // 인증번호 5분 유효

    @Override
    @Transactional(readOnly = true)
    public void checkAndSendAuthNumber(String phoneNumber) {
        boolean exists = memberRepository.existsByPhoneNum(phoneNumber);

        if (exists) {
            throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
        }
        
        String authNumber = RandomNumberUtil.generateSixDigit();
        redisTemplate.opsForValue().set(phoneNumber, authNumber, AUTH_CODE_TTL);

        String message = "[ASSU] 인증번호: " + authNumber;

        AligoSendResponse response = aligoSmsClient.sendSms(phoneNumber, message, "사용자");

        // 실패 처리
        if (!response.getResult_code().equals("1")) {
            redisTemplate.delete(phoneNumber);
            throw new CustomAuthException(ErrorStatus.FAILED_TO_SEND_SMS);
        }
    }

    @Override
    public void verifyAuthNumber(String phoneNumber, String authNumber) {
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        String stored = valueOps.get(phoneNumber);

        if (stored == null || !stored.equals(authNumber)) {
            throw new CustomAuthException(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER);
        }

        redisTemplate.delete(phoneNumber);
    }
}
