package com.assu.server.domain.student.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.entity.UserPaper;
import com.assu.server.support.CommonMockConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class StudentPartnershipQueryCountTest {

    private static final int ADMIN_COUNT = 3;
    private static final int PARTNER_COUNT = 3;

    @Autowired
    private StudentService studentService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager em;

    private Statistics statistics;
    private Long studentId;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();

        Student student = persistStudent();
        studentId = student.getId();

        Admin[] admins = new Admin[ADMIN_COUNT];
        for (int i = 0; i < ADMIN_COUNT; i++) {
            admins[i] = persistAdmin("학생회" + i);
        }
        for (int j = 0; j < PARTNER_COUNT; j++) {
            Partner partner = persistPartner("업체" + j);
            Store store = Store.builder()
                    .partner(partner)
                    .name("가게" + j)
                    .isActivate(ActivationStatus.ACTIVE)
                    .build();
            em.persist(store);
            for (Admin admin : admins) {
                Paper paper = Paper.builder()
                        .admin(admin)
                        .partner(partner)
                        .store(store)
                        .isActivated(ActivationStatus.ACTIVE)
                        .build();
                em.persist(paper);
                PaperContent content = PaperContent.builder()
                        .paper(paper)
                        .note("혜택")
                        .build();
                em.persist(content);
                em.persist(UserPaper.builder()
                        .student(student)
                        .paper(paper)
                        .paperContent(content)
                        .build());
            }
        }
        em.flush();
        em.clear();
        statistics.clear();
    }

    @Test
    void findWithProfilesById_loadsMemberAndProfilesInSingleQuery() {
        // when
        Member member = memberRepository.findWithProfilesById(studentId).orElseThrow();

        // then
        assertThat(executedQueries("findWithProfilesById")).isEqualTo(1);
        em.clear();
        assertThat(member.getStudentProfile().getName()).isEqualTo("학생");
        assertThat(executedQueries("afterProfileAccess")).isEqualTo(1);
    }

    @Test
    void getUsablePartnership_queryCountDoesNotGrowWithPartnerships() {
        // when
        studentService.getUsablePartnership(studentId, true, null, null);

        // then
        assertThat(executedQueries("getUsablePartnership")).isLessThanOrEqualTo(4);
    }

    @Test
    void getRecommendCarouselPartnership_queryCountDoesNotGrowWithPartnerships() {
        // when
        studentService.getRecommendCarouselPartnership(studentId);

        // then
        assertThat(executedQueries("getRecommendCarouselPartnership")).isLessThanOrEqualTo(4);
    }

    private long executedQueries(String label) {
        long count = statistics.getPrepareStatementCount();
        System.out.println("[query-count] " + label + " = " + count);
        return count;
    }

    private Student persistStudent() {
        Member member = persistMember(UserRole.STUDENT);
        Student student = Student.builder()
                .member(member)
                .name("학생")
                .university(University.SSU)
                .department(Department.HUMANITIES)
                .build();
        em.persist(student);
        return student;
    }

    private Admin persistAdmin(String name) {
        Admin admin = Admin.builder()
                .member(persistMember(UserRole.ADMIN))
                .name(name)
                .university(University.SSU)
                .isPhoneVerified(true)
                .officeAddress("서울특별시 동작구 상도로 369")
                .build();
        em.persist(admin);
        return admin;
    }

    private Partner persistPartner(String name) {
        Partner partner = Partner.builder()
                .member(persistMember(UserRole.PARTNER))
                .name(name)
                .isPhoneVerified(true)
                .address("서울특별시 동작구 상도로 1")
                .build();
        em.persist(partner);
        return partner;
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
