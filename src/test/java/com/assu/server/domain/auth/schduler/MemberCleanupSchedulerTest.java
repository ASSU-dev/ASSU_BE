package com.assu.server.domain.auth.schduler;

import com.assu.server.domain.appreview.entity.AppReview;
import com.assu.server.domain.appreview.repository.AppReviewRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.deviceToken.entity.DeviceToken;
import com.assu.server.domain.deviceToken.repository.DeviceTokenRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import com.assu.server.domain.report.entity.enums.ReportType;
import com.assu.server.domain.report.repository.ReportRepository;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(CommonMockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberCleanupSchedulerTest {

    @Autowired
    private MemberCleanupScheduler memberCleanupScheduler;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AppReviewRepository appReviewRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("핵심 리소스(Store)를 보유한 회원은 스킵하고, 나머지 탈퇴 회원은 자식 레코드 정리 후 삭제/익명화된다")
    void cleanupDeletedMembers_whenOneMemberHasCoreResource_thenOthersAreStillDeleted() {
        LocalDateTime oldEnough = LocalDateTime.now().minusMonths(2).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime tooRecent = LocalDateTime.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);

        TransactionTemplate setupTransaction = new TransactionTemplate(transactionManager);
        Member[] fixtures = setupTransaction.execute(status -> {
            Member studentA = createStudentMember(oldEnough);
            Member studentC = createStudentMember(oldEnough);
            Member partnerWithStore = createPartnerMemberWithStore(oldEnough);
            Member recentlyWithdrawn = createStudentMember(tooRecent);

            reviewRepository.save(Review.builder()
                    .student(studentRepository.findById(studentA.getId()).orElseThrow())
                    .rate(5)
                    .content("맛있어요")
                    .build());

            appReviewRepository.save(AppReview.builder()
                    .member(studentA)
                    .rate(5)
                    .content("좋아요")
                    .build());

            deviceTokenRepository.save(DeviceToken.builder()
                    .member(studentA)
                    .token("test-device-token")
                    .active(true)
                    .build());

            reportRepository.save(Report.builder()
                    .reporter(studentA)
                    .reported(studentC)
                    .targetType(ReportTargetType.STUDENT_USER)
                    .targetId(studentC.getId())
                    .reportType(ReportType.STUDENT_USER_OTHER)
                    .build());

            return new Member[]{studentA, studentC, partnerWithStore, recentlyWithdrawn};
        });

        Member studentA = fixtures[0];
        Member studentC = fixtures[1];
        Member partnerWithStore = fixtures[2];
        Member recentlyWithdrawn = fixtures[3];

        Long reviewId = reviewRepository.findAll().stream()
                .filter(r -> r.getStudent() != null && r.getStudent().getId().equals(studentA.getId()))
                .findFirst().orElseThrow().getId();
        Long appReviewId = appReviewRepository.findAll().stream()
                .filter(a -> a.getMember().getId().equals(studentA.getId()))
                .findFirst().orElseThrow().getId();
        Long deviceTokenId = deviceTokenRepository.findAllByMemberIdAndActiveTrue(studentA.getId())
                .stream().findFirst().orElseThrow().getId();
        Long reportId = reportRepository.findAll().stream()
                .filter(r -> r.getTargetId().equals(studentC.getId()) && r.getTargetType() == ReportTargetType.STUDENT_USER)
                .findFirst().orElseThrow().getId();

        memberCleanupScheduler.cleanupDeletedMembers();

        assertThat(memberRepository.findById(studentA.getId())).isEmpty();
        assertThat(memberRepository.findById(studentC.getId())).isEmpty();
        assertThat(appReviewRepository.findById(appReviewId)).isEmpty();
        assertThat(deviceTokenRepository.findById(deviceTokenId)).isEmpty();

        Review anonymizedReview = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(anonymizedReview.getStudent()).isNull();

        Report anonymizedReport = reportRepository.findById(reportId).orElseThrow();
        assertThat(anonymizedReport.getReporter()).isNull();
        assertThat(anonymizedReport.getReported()).isNull();

        Member survivingPartner = memberRepository.findById(partnerWithStore.getId()).orElseThrow();
        assertThat(survivingPartner.getDeletedAt()).isNotNull();
        assertThat(storeRepository.findByPartnerId(partnerWithStore.getId())).isPresent();

        Member survivingRecentMember = memberRepository.findById(recentlyWithdrawn.getId()).orElseThrow();
        assertThat(survivingRecentMember.getDeletedAt()).isEqualTo(recentlyWithdrawn.getDeletedAt());
    }

    private Member createStudentMember(LocalDateTime deletedAt) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.STUDENT)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .deletedAt(deletedAt)
                .build());

        Student student = studentRepository.save(Student.builder()
                .member(member)
                .build());
        member.setStudentProfile(student);

        return memberRepository.save(member);
    }

    private Member createPartnerMemberWithStore(LocalDateTime deletedAt) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.PARTNER)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .deletedAt(deletedAt)
                .build());

        Partner partner = partnerRepository.save(Partner.builder()
                .member(member)
                .name("Test Partner")
                .phoneNum("01012345678")
                .isPhoneVerified(true)
                .address("Seoul")
                .detailAddress("Detail")
                .latitude(37.0)
                .longitude(127.0)
                .build());
        member.setPartnerProfile(partner);

        storeRepository.save(Store.builder()
                .partner(partner)
                .rate(0)
                .isActivate(ActivationStatus.ACTIVE)
                .name("Test Store")
                .address("Seoul")
                .detailAddress("Detail")
                .latitude(37.0)
                .longitude(127.0)
                .build());

        return memberRepository.save(member);
    }
}
