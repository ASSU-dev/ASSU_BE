package com.assu.server.domain.store.repository;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class StoreRepositoryWithdrawnFilterTest {

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Test
    void findAllExcludingWithdrawnPartner_whenPartnerWithdrawn_thenExcludesOnlyThatStore() {
        // given
        Store activePartnerStore = createStore(createPartner("01011112222", false));
        Store withdrawnPartnerStore = createStore(createPartner("01033334444", true));
        Store partnerlessStore = createStore(null);

        // when
        List<Store> stores = storeRepository.findAllExcludingWithdrawnPartner();

        // then
        assertThat(stores)
                .contains(activePartnerStore, partnerlessStore)
                .doesNotContain(withdrawnPartnerStore);
    }

    private Partner createPartner(String phoneNum, boolean isWithdrawn) {
        Member member = Member.builder()
                .role(UserRole.PARTNER)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build();
        if (isWithdrawn) {
            member.withdraw();
        }
        memberRepository.save(member);

        return partnerRepository.save(Partner.builder()
                .member(member)
                .name("Test Partner")
                .phoneNum(phoneNum)
                .isPhoneVerified(true)
                .address("Seoul")
                .detailAddress("Detail")
                .licenseUrl("partners/1/license.png")
                .latitude(37.0)
                .longitude(127.0)
                .build());
    }

    private Store createStore(Partner partner) {
        return storeRepository.save(Store.builder()
                .partner(partner)
                .rate(0)
                .isActivate(ActivationStatus.ACTIVE)
                .name("Test Store")
                .address("Seoul")
                .detailAddress("Detail")
                .latitude(37.0)
                .longitude(127.0)
                .build());
    }
}
