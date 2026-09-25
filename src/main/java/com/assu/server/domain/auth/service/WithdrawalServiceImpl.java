package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.deviceToken.repository.DeviceTokenRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import io.jsonwebtoken.Claims;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalServiceImpl implements WithdrawalService {

    private final MemberRepository memberRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final JwtUtil jwtUtil;

    @Override
    public void withdrawCurrentUser(String authorization) {
        String rawAccessToken = jwtUtil.getTokenFromHeader(authorization);

        Claims claims = jwtUtil.validateTokenOnlySignature(rawAccessToken);
        Long memberId = ((Number) claims.get("userId")).longValue();

        withdrawMember(memberId);

        jwtUtil.blacklistAccess(rawAccessToken);
    }

    @Override
    public void withdrawMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
        withdrawMember(member);
    }

    @Override
    public void withdrawMember(Member member) {
        if (member.isWithdrawn()) {
            throw new CustomAuthException(ErrorStatus.MEMBER_ALREADY_WITHDRAWN);
        }

        member.withdraw();
        memberRepository.save(member);

        deviceTokenRepository.deleteAllByMemberId(member.getId());
        jwtUtil.removeAllRefreshTokens(member.getId());
        jwtUtil.revokeAllAccessTokensSince(member.getId());
    }
}
