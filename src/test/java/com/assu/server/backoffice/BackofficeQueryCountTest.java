package com.assu.server.backoffice;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.auth.entity.SSUAuth;
import com.assu.server.domain.backoffice.dto.BackofficeHomeAutoRecommendResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeMemberSummaryDTO;
import com.assu.server.domain.backoffice.service.BackofficeHomeCurationService;
import com.assu.server.domain.backoffice.service.BackofficeMemberService;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.support.CommonMockConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class BackofficeQueryCountTest {

    @Autowired
    private BackofficeMemberService backofficeMemberService;

    @Autowired
    private BackofficeHomeCurationService backofficeHomeCurationService;

    @Autowired
    private EntityManager em;

    private Statistics statistics;
    private Admin paperOwner;
    private int sequence;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        paperOwner = persistAdmin(false);
    }

    @Test
    void listMembers_queryCountDoesNotGrowWithMembers() {
        // given
        persistMembersOfEachRole(2);
        long small = countQueries("listMembers(k=2)", () -> backofficeMemberService.listMembers(null, null, null, firstPage()));
        persistMembersOfEachRole(2);

        // when
        long large = countQueries("listMembers(k=4)", () -> backofficeMemberService.listMembers(null, null, null, firstPage()));

        // then
        assertThat(large).isEqualTo(small);
        assertThat(large).isLessThanOrEqualTo(1);
    }

    @Test
    void listMembers_returnsProfileFieldsForEachRole() {
        // given
        Student student = persistStudent(false);
        Admin admin = persistAdmin(true);
        Partner partner = persistPartner(true, false);
        Student withdrawnStudent = persistStudent(true);
        em.flush();
        em.clear();

        // when
        PageResponseDTO<BackofficeMemberSummaryDTO> page =
                backofficeMemberService.listMembers(null, null, null, firstPage());
        PageResponseDTO<BackofficeMemberSummaryDTO> deleted =
                backofficeMemberService.listMembers(null, null, true, firstPage());

        // then
        Map<Long, BackofficeMemberSummaryDTO> byId = page.items().stream()
                .collect(Collectors.toMap(BackofficeMemberSummaryDTO::memberId, Function.identity()));
        assertThat(page.totalElements()).isEqualTo(page.items().size());
        assertThat(byId.get(student.getId()).name()).isEqualTo(student.getName());
        assertThat(byId.get(student.getId()).studentNumber()).isEqualTo("2026" + student.getId());
        assertThat(byId.get(student.getId()).email()).isNull();
        assertThat(byId.get(admin.getId()).name()).isEqualTo(admin.getName());
        assertThat(byId.get(admin.getId()).email()).isEqualTo("member" + admin.getId() + "@assu.com");
        assertThat(byId.get(admin.getId()).isSignVerified()).isTrue();
        assertThat(byId.get(partner.getId()).name()).isEqualTo(partner.getName());
        assertThat(byId.get(partner.getId()).email()).isEqualTo("member" + partner.getId() + "@assu.com");
        assertThat(byId.get(partner.getId()).isLicenseVerified()).isTrue();
        assertThat(byId.get(partner.getId()).studentNumber()).isNull();
        assertThat(deleted.items())
                .extracting(BackofficeMemberSummaryDTO::memberId)
                .contains(withdrawnStudent.getId())
                .doesNotContain(student.getId());
    }

    @Test
    void getAutoRecommendedCuration_queryCountDoesNotGrowWithStores() {
        // given
        persistRatedStores(2);
        long small = countQueries("getAutoRecommendedCuration(k=2)",
                () -> backofficeHomeCurationService.getAutoRecommendedCuration());
        persistRatedStores(2);

        // when
        long large = countQueries("getAutoRecommendedCuration(k=4)",
                () -> backofficeHomeCurationService.getAutoRecommendedCuration());

        // then
        assertThat(large).isEqualTo(small);
        assertThat(large).isLessThanOrEqualTo(2);
    }

    @Test
    void getAutoRecommendedCuration_returnsTopRatedStoresWithLatestContent() {
        // given
        Store best = persistStore(100_000, false);
        Store withdrawn = persistStore(90_000, true);
        Store tiedFirst = persistStore(80_000, false);
        Store tiedSecond = persistStore(80_000, false);
        Store fifth = persistStore(70_000, false);
        Paper paper = persistPaper(best);
        persistContent(paper, "1만원 이상", "음료");
        persistContent(paper, "2만원 이상", "안주");
        Paper withdrawnPaper = persistPaper(withdrawn);
        persistContent(withdrawnPaper, null, "탈퇴 업체 혜택");

        // when
        BackofficeHomeAutoRecommendResponseDTO result = backofficeHomeCurationService.getAutoRecommendedCuration();

        // then
        assertThat(result.featuredRecommendation().storeId()).isEqualTo(best.getId());
        assertThat(result.featuredRecommendation().discountContent()).isEqualTo("2만원 이상 안주");
        assertThat(result.curationLists().get(0).stores())
                .extracting(BackofficeHomeAutoRecommendResponseDTO.AutoStoreDTO::storeId)
                .containsExactly(withdrawn.getId(), tiedFirst.getId());
        assertThat(result.curationLists().get(0).stores().get(0).discountContent()).isEqualTo("탈퇴 업체 혜택");
        assertThat(result.curationLists().get(1).stores())
                .extracting(BackofficeHomeAutoRecommendResponseDTO.AutoStoreDTO::storeId)
                .containsExactly(tiedSecond.getId(), fifth.getId());
        assertThat(result.curationLists().get(1).stores().get(0).discountContent()).isEqualTo("제휴 혜택 제공");
    }

    private PageRequest firstPage() {
        return PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "id"));
    }

    private long countQueries(String label, Supplier<?> action) {
        em.flush();
        em.clear();
        statistics.clear();
        action.get();
        long count = statistics.getPrepareStatementCount();
        System.out.println("[query-count] " + label + " = " + count);
        return count;
    }

    private void persistMembersOfEachRole(int count) {
        for (int i = 0; i < count; i++) {
            persistStudent(false);
            persistAdmin(i % 2 == 0);
            persistPartner(i % 2 == 0, false);
        }
    }

    private void persistRatedStores(int count) {
        for (int i = 0; i < count; i++) {
            Store store = persistStore(50_000 + sequence, false);
            Paper paper = persistPaper(store);
            persistContent(paper, null, "혜택");
            persistContent(paper, "조건", "최신 혜택");
        }
    }

    private Store persistStore(Integer rate, boolean partnerWithdrawn) {
        sequence++;
        Store store = Store.builder()
                .partner(persistPartner(false, partnerWithdrawn))
                .name("가게" + sequence)
                .rate(rate)
                .isActivate(ActivationStatus.ACTIVE)
                .build();
        em.persist(store);
        return store;
    }

    private Paper persistPaper(Store store) {
        Paper paper = Paper.builder()
                .admin(paperOwner)
                .partner(store.getPartner())
                .store(store)
                .isActivated(ActivationStatus.ACTIVE)
                .build();
        em.persist(paper);
        return paper;
    }

    private void persistContent(Paper paper, String condition, String benefit) {
        em.persist(PaperContent.builder()
                .paper(paper)
                .benefitCondition(condition)
                .benefit(benefit)
                .build());
    }

    private Student persistStudent(boolean withdrawn) {
        Member member = persistMember(UserRole.STUDENT, withdrawn);
        Student student = Student.builder()
                .member(member)
                .name("학생" + member.getId())
                .university(University.SSU)
                .department(Department.HUMANITIES)
                .build();
        em.persist(student);
        em.persist(SSUAuth.builder()
                .member(member)
                .studentNumber("2026" + member.getId())
                .isAuthenticated(true)
                .authenticatedAt(LocalDateTime.now())
                .build());
        return student;
    }

    private Admin persistAdmin(boolean signVerified) {
        Member member = persistMember(UserRole.ADMIN, false);
        persistCommonAuth(member);
        Admin admin = Admin.builder()
                .member(member)
                .name("학생회" + member.getId())
                .university(University.SSU)
                .isPhoneVerified(true)
                .isSignVerified(signVerified)
                .officeAddress("서울특별시 동작구 상도로 369")
                .build();
        em.persist(admin);
        return admin;
    }

    private Partner persistPartner(boolean licenseVerified, boolean withdrawn) {
        Member member = persistMember(UserRole.PARTNER, withdrawn);
        persistCommonAuth(member);
        Partner partner = Partner.builder()
                .member(member)
                .name("업체" + member.getId())
                .isPhoneVerified(true)
                .isLicenseVerified(licenseVerified)
                .address("서울특별시 동작구 상도로 1")
                .build();
        em.persist(partner);
        return partner;
    }

    private void persistCommonAuth(Member member) {
        em.persist(CommonAuth.builder()
                .member(member)
                .email("member" + member.getId() + "@assu.com")
                .hashedPassword("hashed")
                .lastLoginAt(LocalDateTime.now())
                .build());
    }

    private Member persistMember(UserRole role, boolean withdrawn) {
        Member member = Member.builder()
                .role(role)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build();
        if (withdrawn) {
            member.withdraw();
        }
        em.persist(member);
        return member;
    }
}
