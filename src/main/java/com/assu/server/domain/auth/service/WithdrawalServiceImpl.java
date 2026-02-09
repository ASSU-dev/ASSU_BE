package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.security.jwt.JwtUtil;
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
    private final JwtUtil jwtUtil;

    @Override
    public void withdrawCurrentUser(String authorization) {
        String rawAccessToken = jwtUtil.getTokenFromHeader(authorization);

        Claims claims = jwtUtil.validateTokenOnlySignature(rawAccessToken);
        Long memberId = ((Number) claims.get("userId")).longValue();

        withdrawMember(memberId);

        jwtUtil.blacklistAccess(rawAccessToken);
    }

    private void withdrawMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));

        if (member.getDeletedAt() != null) {
            throw new CustomAuthException(ErrorStatus.MEMBER_ALREADY_WITHDRAWN);
        }

        // 소프트 삭제 처리
        member.setDeletedAt(java.time.LocalDateTime.now());
        memberRepository.save(member);

        jwtUtil.removeAllRefreshTokens(memberId);
    }
}
