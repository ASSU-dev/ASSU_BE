package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.deviceToken.repository.DeviceTokenRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import io.jsonwebtoken.Claims;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalServiceImpl implements WithdrawalService {

    private final MemberRepository memberRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PaperRepository paperRepository;
    private final JwtUtil jwtUtil;
    private final PhoneAuthService phoneAuthService;

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

        if (member.getRole() == UserRole.PARTNER) {
            paperRepository.deactivateByPartnerWithdrawal(
                    member.getId(),
                    List.of(ActivationStatus.ACTIVE, ActivationStatus.SUSPEND, ActivationStatus.BLANK),
                    ActivationStatus.INACTIVE
            );
        }

        deviceTokenRepository.deleteAllByMemberId(member.getId());
        jwtUtil.removeAllRefreshTokens(member.getId());
        jwtUtil.revokeAllAccessTokensSince(member.getId());
    }

    @Override
    public void restoreMember(Member member) {
        // 탈퇴 기간 중 다른 회원이 같은 번호로 가입했을 수 있다
        String phoneNum = resolvePhoneNum(member);
        if (phoneNum != null) {
            phoneAuthService.assertPhoneNotInUse(phoneNum);
        }

        member.restore();
        memberRepository.save(member);

        if (member.getRole() == UserRole.PARTNER) {
            paperRepository.restoreByPartnerRestoration(member.getId());
        }
    }

    private String resolvePhoneNum(Member member) {
        if (member.getRole() == UserRole.PARTNER && member.getPartnerProfile() != null) {
            return member.getPartnerProfile().getPhoneNum();
        }
        if (member.getRole() == UserRole.ADMIN && member.getAdminProfile() != null) {
            return member.getAdminProfile().getPhoneNum();
        }
        return null;
    }
}
