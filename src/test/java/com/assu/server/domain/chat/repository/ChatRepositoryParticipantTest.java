package com.assu.server.domain.chat.repository;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.chat.entity.ChattingRoom;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class ChatRepositoryParticipantTest {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void existsByIdAndParticipantId_whenBothSidesPresent_thenOnlyParticipantsMatch() {
        // given
        Admin admin = createAdmin();
        Partner partner = createPartner();
        Long outsiderId = createMember(UserRole.PARTNER).getId();
        ChattingRoom room = chatRepository.save(ChattingRoom.of(admin, partner));

        // when & then
        assertThat(chatRepository.existsByIdAndParticipantId(room.getId(), admin.getId())).isTrue();
        assertThat(chatRepository.existsByIdAndParticipantId(room.getId(), partner.getId())).isTrue();
        assertThat(chatRepository.existsByIdAndParticipantId(room.getId(), outsiderId)).isFalse();
        assertThat(chatRepository.existsByIdAndParticipantId(room.getId() + 1, admin.getId())).isFalse();
    }

    @Test
    void existsByIdAndParticipantId_whenOneSideLeft_thenRemainingParticipantMatches() {
        // given
        Admin admin = createAdmin();
        Partner partner = createPartner();
        ChattingRoom adminOnly = chatRepository.save(ChattingRoom.of(admin, null));
        ChattingRoom partnerOnly = chatRepository.save(ChattingRoom.of(null, partner));

        // when & then
        assertThat(chatRepository.existsByIdAndParticipantId(adminOnly.getId(), admin.getId())).isTrue();
        assertThat(chatRepository.existsByIdAndParticipantId(adminOnly.getId(), partner.getId())).isFalse();
        assertThat(chatRepository.existsByIdAndParticipantId(partnerOnly.getId(), partner.getId())).isTrue();
        assertThat(chatRepository.existsByIdAndParticipantId(partnerOnly.getId(), admin.getId())).isFalse();
    }

    private Member createMember(UserRole role) {
        return memberRepository.save(Member.builder()
                .role(role)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());
    }

    private Admin createAdmin() {
        return adminRepository.save(Admin.builder()
                .member(createMember(UserRole.ADMIN))
                .name("숭실 총학생회")
                .university(University.SSU)
                .isPhoneVerified(true)
                .officeAddress("서울특별시 동작구 상도로 369")
                .build());
    }

    private Partner createPartner() {
        return partnerRepository.save(Partner.builder()
                .member(createMember(UserRole.PARTNER))
                .name("역전할머니 맥주")
                .isPhoneVerified(true)
                .address("서울특별시 동작구")
                .build());
    }
}
