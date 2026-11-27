package com.assu.server.domain.partnership.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.backoffice.service.BackofficePartnershipService;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.support.CommonMockConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class PartnershipListPagingTest {

    private static final int PAGE_SIZE = 2;

    @Autowired
    private PartnershipService partnershipService;

    @Autowired
    private BackofficePartnershipService backofficePartnershipService;

    @Autowired
    private EntityManager em;

    private Statistics statistics;
    private Admin admin;
    private Partner partner;
    private Store store;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        admin = persistAdmin();
        partner = persistPartner();
        store = Store.builder()
                .partner(partner)
                .name("가게")
                .isActivate(ActivationStatus.ACTIVE)
                .build();
        em.persist(store);
    }

    @Test
    void listPartnershipsForAdmin_whenSomePapersHaveNoStore_thenPageAndTotalExcludeThem() {
        // given
        persistPapers(admin, store, 3);
        persistPapers(admin, null, 2);

        // when / then
        assertConsistentPaging(pageable -> partnershipService.listPartnershipsForAdmin(pageable, admin.getId()), 3);
    }

    @Test
    void listPartnershipsForPartner_whenSomePapersHaveNoAdmin_thenPageAndTotalExcludeThem() {
        // given
        persistPapers(admin, store, 3);
        persistPapers(null, store, 2);

        // when / then
        assertConsistentPaging(pageable -> partnershipService.listPartnershipsForPartner(pageable, partner.getId()), 3);
    }

    @Test
    void getPartnershipsByAdmin_whenSomePapersHaveNoStore_thenPageAndTotalExcludeThem() {
        // given
        persistPapers(admin, store, 3);
        persistPapers(admin, null, 2);

        // when / then
        assertConsistentPaging(pageable -> backofficePartnershipService.getPartnershipsByAdmin(admin.getId(), pageable), 3);
    }

    @Test
    void getPartnershipsByStore_whenSomePapersHaveNoAdmin_thenPageAndTotalExcludeThem() {
        // given
        persistPapers(admin, store, 3);
        persistPapers(null, store, 2);

        // when / then
        assertConsistentPaging(pageable -> backofficePartnershipService.getPartnershipsByStore(store.getId(), pageable), 3);
    }

    @Test
    void getPartnerships_whenSomePapersHaveNoAdminOrStore_thenPageAndTotalExcludeThem() {
        // given
        long baseline = backofficePartnershipService.getPartnerships(PageRequest.of(0, 1)).getTotalElements();
        persistPapers(admin, store, 3);
        persistPapers(null, store, 1);
        persistPapers(admin, null, 1);
        em.flush();
        em.clear();

        // when
        Page<WritePartnershipResponseDTO> page = backofficePartnershipService.getPartnerships(
                PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));

        // then
        assertThat(page.getTotalElements()).isEqualTo(baseline + 3);
        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        assertThat(page.getContent()).allSatisfy(dto -> {
            assertThat(dto.adminName()).isNotNull();
            assertThat(dto.storeName()).isNotNull();
        });
    }

    @Test
    void listPartnershipsForAdmin_queryCountDoesNotGrowWithPapers() {
        // given
        persistPapers(admin, store, 2);
        long small = countQueries(() -> partnershipService.listPartnershipsForAdmin(PageRequest.of(0, 10), admin.getId()));
        persistPapers(admin, store, 2);

        // when
        long large = countQueries(() -> partnershipService.listPartnershipsForAdmin(PageRequest.of(0, 10), admin.getId()));

        // then
        assertThat(large).isEqualTo(small);
        assertThat(large).isLessThanOrEqualTo(2);
    }

    private void assertConsistentPaging(Function<Pageable, Page<WritePartnershipResponseDTO>> query, int validCount) {
        em.flush();
        em.clear();
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        Page<WritePartnershipResponseDTO> first = query.apply(PageRequest.of(0, PAGE_SIZE, sort));
        Page<WritePartnershipResponseDTO> second = query.apply(PageRequest.of(1, PAGE_SIZE, sort));

        assertThat(first.getTotalElements()).isEqualTo(validCount);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).hasSize(PAGE_SIZE);
        assertThat(second.getContent()).hasSize(validCount - PAGE_SIZE);
        assertThat(first.getContent()).allSatisfy(dto -> {
            assertThat(dto.adminName()).isEqualTo(admin.getName());
            assertThat(dto.storeName()).isEqualTo(store.getName());
            assertThat(dto.options()).hasSize(1);
        });
    }

    private long countQueries(Runnable action) {
        em.flush();
        em.clear();
        statistics.clear();
        action.run();
        long count = statistics.getPrepareStatementCount();
        System.out.println("[query-count] listPartnershipsForAdmin = " + count);
        return count;
    }

    private void persistPapers(Admin owner, Store target, int count) {
        for (int i = 0; i < count; i++) {
            Paper paper = Paper.builder()
                    .admin(owner)
                    .partner(partner)
                    .store(target)
                    .isActivated(ActivationStatus.ACTIVE)
                    .build();
            em.persist(paper);
            em.persist(PaperContent.builder()
                    .paper(paper)
                    .benefit("혜택")
                    .build());
        }
    }

    private Admin persistAdmin() {
        Admin created = Admin.builder()
                .member(persistMember(UserRole.ADMIN))
                .name("총학생회")
                .university(University.SSU)
                .isPhoneVerified(true)
                .officeAddress("서울특별시 동작구 상도로 369")
                .build();
        em.persist(created);
        return created;
    }

    private Partner persistPartner() {
        Partner created = Partner.builder()
                .member(persistMember(UserRole.PARTNER))
                .name("업체")
                .isPhoneVerified(true)
                .address("서울특별시 동작구 상도로 1")
                .build();
        em.persist(created);
        return created;
    }

    private Member persistMember(UserRole role) {
        Member member = Member.builder()
                .role(role)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build();
        em.persist(member);
        return member;
    }
}
