package com.assu.server.domain.student.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.entity.enums.StoreCategory;
import com.assu.server.domain.student.dto.StudentHomeResponseDTO;
import com.assu.server.domain.student.dto.StudentResponseDTO;
import com.assu.server.domain.student.entity.PartnershipUsage;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.HomeCurationRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class StudentHomeAndUsageQueryCountTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private HomeCurationRepository homeCurationRepository;

    @Autowired
    private EntityManager em;

    private Statistics statistics;
    private Student student;
    private Admin admin;
    private int sequence;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        homeCurationRepository.deleteAll();
        student = persistStudent();
        admin = persistAdmin();
    }

    @Test
    void getStudentHome_withoutCuration_queryCountDoesNotGrowWithStores() {
        // given
        persistRatedStores(2);
        long small = countQueries("getStudentHome(k=2)", () -> studentService.getStudentHome(student.getId()));
        persistRatedStores(2);

        // when
        long large = countQueries("getStudentHome(k=4)", () -> studentService.getStudentHome(student.getId()));

        // then
        assertThat(large).isEqualTo(small);
        assertThat(large).isLessThanOrEqualTo(4);
    }

    @Test
    void getStudentHome_withoutCuration_returnsTopRatedActiveStoresWithLatestContent() {
        // given
        Store nullRate = persistStore(null, false);
        Store tiedFirst = persistStore(9_000, false);
        Store withdrawn = persistStore(9_999, true);
        Store best = persistStore(10_000, false);
        Store tiedSecond = persistStore(9_000, false);
        Store low = persistStore(1, false);
        Store lowest = persistStore(0, false);
        Paper olderPaper = persistPaper(best);
        persistContent(olderPaper, "1만원 이상", "음료", null);
        Paper newerPaper = persistPaper(best);
        persistContent(newerPaper, null, "이전 혜택", null);
        persistContent(newerPaper, "2만원 이상", "안주", "학생증");
        persistContent(olderPaper, null, "가장 최신 혜택", null);

        // when
        StudentHomeResponseDTO home = studentService.getStudentHome(student.getId());

        // then
        assertThat(home.featuredRecommendation().storeId()).isEqualTo(best.getId());
        assertThat(home.featuredRecommendation().discountContent()).isEqualTo("가장 최신 혜택");
        assertThat(home.curationLists().get(0).stores())
                .extracting(StudentHomeResponseDTO.CurationStoreDTO::storeId)
                .containsExactly(tiedFirst.getId(), tiedSecond.getId());
        assertThat(home.curationLists().get(1).stores())
                .extracting(StudentHomeResponseDTO.CurationStoreDTO::storeId)
                .containsExactly(low.getId(), lowest.getId());
        assertThat(home.curationLists().get(0).stores())
                .extracting(StudentHomeResponseDTO.CurationStoreDTO::discountContent)
                .containsOnly("제휴 혜택 제공");
        assertThat(List.of(nullRate.getId(), withdrawn.getId()))
                .doesNotContain(home.featuredRecommendation().storeId());
    }

    @Test
    void getMyPartnership_queryCountDoesNotGrowWithUsages() {
        // given
        LocalDate today = LocalDate.now();
        persistUsages(2, today, false);
        long small = countQueries("getMyPartnership(k=2)",
                () -> studentService.getMyPartnership(student.getId(), today.getYear(), today.getMonthValue()));
        persistUsages(2, today, false);

        // when
        long large = countQueries("getMyPartnership(k=4)",
                () -> studentService.getMyPartnership(student.getId(), today.getYear(), today.getMonthValue()));

        // then
        assertThat(large).isEqualTo(small);
        assertThat(large).isLessThanOrEqualTo(2);
    }

    @Test
    void getMyPartnership_returnsOnlyUsagesInMonthWithStoreAndPartner() {
        // given
        LocalDate firstDay = LocalDate.of(2026, 3, 1);
        List<PartnershipUsage> inMonth = new ArrayList<>();
        inMonth.addAll(persistUsages(1, firstDay, false));
        inMonth.addAll(persistUsages(1, LocalDate.of(2026, 3, 31), true));
        persistUsages(1, LocalDate.of(2026, 2, 28), false);
        persistUsages(1, LocalDate.of(2026, 4, 1), false);
        PartnershipUsage missingContent = persistUsage(LocalDate.of(2026, 3, 15), false, 999_999L);
        em.flush();
        em.clear();

        // when
        StudentResponseDTO.MyPartnership result = studentService.getMyPartnership(student.getId(), 2026, 3);

        // then
        assertThat(result.serviceCount()).isEqualTo(3);
        assertThat(result.details())
                .extracting(StudentResponseDTO.UsageDetail::partnershipUsageId)
                .containsExactly(inMonth.get(1).getId(), missingContent.getId(), inMonth.get(0).getId());
        StudentResponseDTO.UsageDetail last = result.details().get(0);
        PaperContent content = em.find(PaperContent.class, inMonth.get(1).getContentId());
        assertThat(last.storeId()).isEqualTo(content.getPaper().getStore().getId());
        assertThat(last.partnerId()).isEqualTo(content.getPaper().getStore().getPartner().getId());
        assertThat(result.details().get(1).storeId()).isNull();
        assertThat(result.details().get(1).partnerId()).isNull();
    }

    @Test
    void getMyPartnership_whenMonthIsInvalid_thenReturnsEmpty() {
        // when
        StudentResponseDTO.MyPartnership result = studentService.getMyPartnership(student.getId(), 2026, 13);

        // then
        assertThat(result.serviceCount()).isZero();
        assertThat(result.details()).isEmpty();
    }

    @Test
    void getUnreviewedUsage_queryCountDoesNotGrowWithUsages() {
        // given
        persistUsages(2, LocalDate.now(), false);
        long small = countQueries("getUnreviewedUsage(k=2)",
                () -> studentService.getUnreviewedUsage(student.getId(), PageRequest.of(1, 10)));
        persistUsages(2, LocalDate.now(), false);

        // when
        long large = countQueries("getUnreviewedUsage(k=4)",
                () -> studentService.getUnreviewedUsage(student.getId(), PageRequest.of(1, 10)));

        // then
        assertThat(large).isEqualTo(small);
        assertThat(large).isLessThanOrEqualTo(2);
    }

    @Test
    void getUnreviewedUsage_returnsOnlyUnreviewedWithStoreAndPartner() {
        // given
        List<PartnershipUsage> unreviewed = persistUsages(3, LocalDate.now(), false);
        persistUsages(2, LocalDate.now(), true);
        em.flush();
        em.clear();

        // when
        Page<StudentResponseDTO.UsageDetail> page =
                studentService.getUnreviewedUsage(student.getId(), PageRequest.of(1, 2));

        // then
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        for (StudentResponseDTO.UsageDetail detail : page.getContent()) {
            PartnershipUsage usage = unreviewed.stream()
                    .filter(u -> u.getId().equals(detail.partnershipUsageId()))
                    .findFirst()
                    .orElseThrow();
            Store store = em.find(PaperContent.class, usage.getContentId()).getPaper().getStore();
            assertThat(detail.storeId()).isEqualTo(store.getId());
            assertThat(detail.partnerId()).isEqualTo(store.getPartner().getId());
            assertThat(detail.isReviewed()).isFalse();
        }
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

    private void persistRatedStores(int count) {
        for (int i = 0; i < count; i++) {
            Store store = persistStore(5_000 + sequence, false);
            Paper paper = persistPaper(store);
            persistContent(paper, null, "혜택", null);
            persistContent(paper, "조건", "최신 혜택", null);
        }
    }

    private List<PartnershipUsage> persistUsages(int count, LocalDate date, boolean isReviewed) {
        List<PartnershipUsage> usages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Store store = persistStore(1, false);
            Paper paper = persistPaper(store);
            PaperContent content = persistContent(paper, null, "혜택", null);
            usages.add(persistUsage(date, isReviewed, content.getId()));
        }
        return usages;
    }

    private PartnershipUsage persistUsage(LocalDate date, boolean isReviewed, Long contentId) {
        PartnershipUsage usage = PartnershipUsage.builder()
                .student(student)
                .place("가게")
                .date(date)
                .partnershipContent("혜택")
                .isReviewed(isReviewed)
                .contentId(contentId)
                .adminName(admin.getName())
                .build();
        em.persist(usage);
        return usage;
    }

    private Store persistStore(Integer rate, boolean partnerWithdrawn) {
        sequence++;
        Partner partner = persistPartner("업체" + sequence, partnerWithdrawn);
        Store store = Store.builder()
                .partner(partner)
                .name("가게" + sequence)
                .rate(rate)
                .storeCategory(StoreCategory.CAFE)
                .isActivate(ActivationStatus.ACTIVE)
                .build();
        em.persist(store);
        return store;
    }

    private Paper persistPaper(Store store) {
        Paper paper = Paper.builder()
                .admin(admin)
                .partner(store.getPartner())
                .store(store)
                .isActivated(ActivationStatus.ACTIVE)
                .build();
        em.persist(paper);
        return paper;
    }

    private PaperContent persistContent(Paper paper, String condition, String benefit, String caution) {
        PaperContent content = PaperContent.builder()
                .paper(paper)
                .benefitCondition(condition)
                .benefit(benefit)
                .caution(caution)
                .build();
        em.persist(content);
        return content;
    }

    private Student persistStudent() {
        Student created = Student.builder()
                .member(persistMember(UserRole.STUDENT, false))
                .name("학생")
                .university(University.SSU)
                .department(Department.HUMANITIES)
                .build();
        em.persist(created);
        return created;
    }

    private Admin persistAdmin() {
        Admin created = Admin.builder()
                .member(persistMember(UserRole.ADMIN, false))
                .name("총학생회")
                .university(University.SSU)
                .isPhoneVerified(true)
                .officeAddress("서울특별시 동작구 상도로 369")
                .build();
        em.persist(created);
        return created;
    }

    private Partner persistPartner(String name, boolean withdrawn) {
        Partner partner = Partner.builder()
                .member(persistMember(UserRole.PARTNER, withdrawn))
                .name(name)
                .isPhoneVerified(true)
                .address("서울특별시 동작구 상도로 1")
                .build();
        em.persist(partner);
        return partner;
    }

    private Member persistMember(UserRole role, boolean withdrawn) {
        Member member = Member.builder()
                .role(role)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .profileUrl("profiles/" + role.name().toLowerCase() + ".png")
                .build();
        if (withdrawn) {
            member.withdraw();
        }
        em.persist(member);
        return member;
    }
}
