package com.assu.server.backoffice;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.auth.service.LoginService;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class LoginServiceSuspendTest {

    @Autowired
    private LoginService loginService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CommonAuthRepository commonAuthRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("SUSPEND 계정 공통 로그인 시 MEMBER_PENDING_APPROVAL 반환")
    void suspendAccountReturnsPendingApprovalError() {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.PARTNER)
                .isActivated(ActivationStatus.SUSPEND)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());

        CommonAuth commonAuth = commonAuthRepository.save(CommonAuth.builder()
                .member(member)
                .email("pending-login@test.com")
                .hashedPassword(passwordEncoder.encode("password123"))
                .lastLoginAt(LocalDateTime.now())
                .build());
        member.setCommonAuth(commonAuth);

        Partner partner = partnerRepository.save(Partner.builder()
                .member(member)
                .name("Pending Partner")
                .phoneNum("01011112222")
                .isPhoneVerified(true)
                .address("Seoul")
                .detailAddress("Detail")
                .licenseUrl("partners/" + member.getId() + "/license.png")
                .latitude(37.0)
                .longitude(127.0)
                .build());
        member.setPartnerProfile(partner);
        memberRepository.save(member);

        assertThatThrownBy(() -> loginService.loginCommon(
                new CommonLoginRequestDTO("pending-login@test.com", "password123")))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.MEMBER_PENDING_APPROVAL);
    }
}
