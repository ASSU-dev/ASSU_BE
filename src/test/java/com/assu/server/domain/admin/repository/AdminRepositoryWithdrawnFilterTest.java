package com.assu.server.domain.admin.repository;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class AdminRepositoryWithdrawnFilterTest {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void adminQueries_whenAdminWithdrawn_thenExcluded() {
        // given
        Admin active = createAdmin("숭실 총학생회", false);
        Admin withdrawn = createAdmin("숭실 탈퇴학생회", true);

        // when
        List<Admin> matching = adminRepository.findMatchingAdmins(University.SSU, Department.HUMANITIES, null);
        List<Admin> searched = adminRepository.searchAdminByKeywordWithMember("숭실", PageRequest.of(0, 10));
        List<Admin> recommended = adminRepository.findPartnerWithOffset(999L, PageRequest.of(0, 10));

        // then
        assertThat(matching).contains(active).doesNotContain(withdrawn);
        assertThat(searched).contains(active).doesNotContain(withdrawn);
        assertThat(recommended).contains(active).doesNotContain(withdrawn);
        assertThat(adminRepository.countPartner(999L)).isEqualTo(recommended.size());
        assertThat(adminRepository.findByIdAndMember_DeletedAtIsNull(withdrawn.getId())).isEmpty();
        assertThat(adminRepository.findByIdAndMember_DeletedAtIsNull(active.getId())).isPresent();
    }

    private Admin createAdmin(String name, boolean isWithdrawn) {
        Member member = Member.builder()
                .role(UserRole.ADMIN)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build();
        if (isWithdrawn) {
            member.withdraw();
        }
        memberRepository.save(member);

        return adminRepository.save(Admin.builder()
                .member(member)
                .name(name)
                .university(University.SSU)
                .isPhoneVerified(true)
                .officeAddress("서울특별시 동작구 상도로 369")
                .build());
    }
}
