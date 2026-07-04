package com.assu.server.backoffice;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.service.WithdrawalService;
import com.assu.server.domain.backoffice.dto.BackofficeMemberSummaryDTO;
import com.assu.server.domain.backoffice.service.BackofficeMemberService;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(BackofficeSecurityIntegrationTest.TestJwtConfig.class)
class BackofficeMemberServiceTest {

    @Autowired
    private BackofficeMemberService backofficeMemberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private WithdrawalService withdrawalService;

    @Test
    @DisplayName("SUSPEND Partner 회원을 승인하면 ACTIVE 및 license verified 처리")
    void approvePartnerMember() {
        Member member = createPartnerMember(ActivationStatus.SUSPEND);

        BackofficeMemberSummaryDTO result = backofficeMemberService.approveMember(member.getId());

        assertThat(result.status()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(result.isLicenseVerified()).isTrue();

        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updated.getIsActivated()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(updated.getPartnerProfile().getIsLicenseVerified()).isTrue();
        assertThat(updated.getPartnerProfile().getLicenseVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("BACKOFFICE 회원은 강제 탈퇴할 수 없다")
    void cannotForceWithdrawBackofficeMember() {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.BACKOFFICE)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());

        assertThatThrownBy(() -> backofficeMemberService.forceWithdrawMember(member.getId()))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.CANNOT_WITHDRAW_BACKOFFICE_MEMBER);
    }

    @Test
    @DisplayName("탈퇴 회원을 복구하면 deletedAt이 null이 된다")
    void restoreDeletedMember() {
        Member member = createPartnerMember(ActivationStatus.ACTIVE);
        withdrawalService.withdrawMember(member.getId());

        BackofficeMemberSummaryDTO result = backofficeMemberService.restoreMember(member.getId());

        assertThat(result.deletedAt()).isNull();
        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updated.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("ACTIVE 회원은 승인할 수 없다")
    void cannotApproveActiveMember() {
        Member member = createPartnerMember(ActivationStatus.ACTIVE);

        assertThatThrownBy(() -> backofficeMemberService.approveMember(member.getId()))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.MEMBER_NOT_PENDING_APPROVAL);
    }

    private Member createPartnerMember(ActivationStatus status) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.PARTNER)
                .isActivated(status)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());

        Partner partner = partnerRepository.save(Partner.builder()
                .member(member)
                .name("Test Partner")
                .phoneNum("01012345678")
                .isPhoneVerified(true)
                .address("Seoul")
                .detailAddress("Detail")
                .licenseUrl("partners/1/license.png")
                .latitude(37.0)
                .longitude(127.0)
                .build());
        member.setPartnerProfile(partner);
        return memberRepository.save(member);
    }
}
