package com.assu.server.domain.student.repository;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.entity.UserPaper;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(CommonMockConfig.class)
class UserPaperUniqueConstraintTest {

    @Autowired
    private UserPaperRepository userPaperRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperContentRepository paperContentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Student student;
    private Paper paper;
    private PaperContent content;

    @BeforeEach
    void setUp() {
        student = transactionTemplate.execute(status -> {
            Member member = memberRepository.save(Member.builder()
                    .role(UserRole.STUDENT)
                    .isActivated(ActivationStatus.ACTIVE)
                    .isLocationTermAgreed(true)
                    .isMarketingTermAgreed(false)
                    .build());
            return studentRepository.save(Student.builder().member(member).build());
        });
        paper = paperRepository.save(Paper.builder()
                .isActivated(ActivationStatus.ACTIVE)
                .partnershipPeriodStart(LocalDate.now().minusDays(1))
                .partnershipPeriodEnd(LocalDate.now().plusDays(30))
                .build());
        content = paperContentRepository.save(PaperContent.builder().paper(paper).build());
    }

    @AfterEach
    void tearDown() {
        userPaperRepository.deleteAll(userPaperRepository.findByStudentId(student.getId()));
        paperContentRepository.deleteById(content.getId());
        paperRepository.deleteById(paper.getId());
        studentRepository.deleteById(student.getId());
        memberRepository.deleteById(student.getId());
    }

    @Test
    void save_whenSameStudentAndContentTwice_thenRejected() {
        saveUserPaper();

        assertThatThrownBy(this::saveUserPaper).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(userPaperRepository.findByStudentId(student.getId())).hasSize(1);
    }

    @Test
    void save_whenTwoPodsSyncSameStudentConcurrently_thenOnlyOneRowAndLoserFailsCleanly() throws Exception {
        // given
        ExecutorService pods = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // when
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(pods.submit(() -> {
                    start.await();
                    saveUserPaper();
                    return null;
                }));
            }
            start.countDown();

            int failures = 0;
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(DataIntegrityViolationException.class);
                    failures++;
                }
            }

            // then
            assertThat(failures).isEqualTo(1);
        } finally {
            pods.shutdownNow();
        }
        assertThat(userPaperRepository.findByStudentId(student.getId())).hasSize(1);
    }

    private void saveUserPaper() {
        transactionTemplate.executeWithoutResult(status -> userPaperRepository.saveAndFlush(UserPaper.builder()
                .student(student)
                .paper(paper)
                .paperContent(content)
                .build()));
    }
}
