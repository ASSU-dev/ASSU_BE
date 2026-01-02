package com.assu.server.domain.deviceToken.service;

import com.assu.server.domain.deviceToken.entity.DeviceToken;
import com.assu.server.domain.deviceToken.repository.DeviceTokenRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceTokenServiceImpl implements DeviceTokenService {
    private final DeviceTokenRepository deviceTokenRepository;
    private final MemberRepository memberRepository;

    @Transactional
    @Override
    public Long register(String token, Long memberId) {
        Member member = memberRepository.findMemberById(memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        // 1) 같은 회원 + 같은 토큰 → 활성화만 복구
        var sameTokenOpt = deviceTokenRepository.findByMemberIdAndToken(memberId, token);

        if (sameTokenOpt.isPresent()) {
            DeviceToken exist = sameTokenOpt.get();
            exist.setActive(true);
            return exist.getId();
        }

        // 2) 같은 회원 + 다른 토큰 → 기존 활성 토큰 비활성화
        List<DeviceToken> activeTokens =
                deviceTokenRepository.findAllByMemberIdAndActiveTrue(memberId);

        for (DeviceToken deviceToken : activeTokens) {
                deviceToken.setActive(false);
        }

        // 3) 신규 토큰 저장
        DeviceToken newToken = DeviceToken.builder()
                .member(member)
                .token(token)
                .active(true)
                .build();

        deviceTokenRepository.save(newToken);
        return newToken.getId();
    }

    @Transactional
    @Override
    public void unregister(Long tokenId, Long memberId) {
        deviceTokenRepository.findById(tokenId)
                .ifPresentOrElse(deviceToken -> {
                    if (!deviceToken.getMember().getId().equals(memberId)) {
                        throw new DatabaseException(ErrorStatus.DEVICE_TOKEN_NOT_OWNED);
                    }
                    deviceToken.setActive(false);
                }, () -> {
                    throw new DatabaseException(ErrorStatus.DEVICE_TOKEN_NOT_FOUND);
                });
    }

    @Transactional
    @Override
    public void deactivateTokens(List<String> invalidTokens) {
        List<DeviceToken> invalidEntities = deviceTokenRepository.findAllByTokenIn(invalidTokens);
        invalidEntities.forEach(dt -> dt.setActive(false));
    }
}