package com.assu.server.domain.notification.service;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.entity.NotificationType;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import com.assu.server.domain.notification.repository.NotificationRepository;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(CommonMockConfig.class)
class OutboxMultiInstanceTest {

    private static final int PODS = 2;

    @Autowired
    private OutboxStatusService outboxStatusService;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        notificationRepository.deleteAll();
        memberRepository.deleteAllById(memberIds);
    }

    @Test
    void claimForRetry_whenTwoPodsRaceOnSameOutbox_thenExactlyOneRepublishes() throws Exception {
        Long outboxId = saveOutbox(NotificationOutbox.Status.PENDING, 0, LocalDateTime.now().minusSeconds(1));

        List<Boolean> results = raceOnPods(() -> outboxStatusService.claimForRetry(outboxId).isPresent());

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(outboxRepository.findById(outboxId).orElseThrow().getRetryCount()).isEqualTo(1);
    }

    @Test
    void claimForRetry_whenOtherPodClaimedWithStaleRetryCount_thenRejected() {
        // given: 두 파드가 retryCount=0을 읽고, 한 파드가 먼저 선점
        Long outboxId = saveOutbox(NotificationOutbox.Status.PENDING, 0, LocalDateTime.now().minusSeconds(1));
        LocalDateTime now = LocalDateTime.now();
        int first = inTx(() -> outboxRepository.claimForRetry(outboxId, 0, now, now.minusSeconds(1)));

        // when: 다른 파드가 읽어둔 retryCount=0으로 선점 시도 (next_attempt_at 조건은 통과하도록 과거 시각)
        int second = inTx(() -> outboxRepository.claimForRetry(outboxId, 0, LocalDateTime.now(), now.minusSeconds(1)));

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void claimForSending_whenSameMessageConsumedOnTwoPods_thenOnlyOneSends() throws Exception {
        Long outboxId = saveOutbox(NotificationOutbox.Status.DISPATCHED, 0, null);

        List<Boolean> results = raceOnPods(() -> outboxStatusService.claimForSending(outboxId));

        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void retryCycle_whenTwoPodsPollTogether_thenEachOutboxRepublishedOnce() throws Exception {
        // given
        LocalDateTime past = LocalDateTime.now().minusSeconds(1);
        List<Long> outboxIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            outboxIds.add(saveOutbox(NotificationOutbox.Status.PENDING, 0, past));
        }

        // when: 두 파드의 재시도 스케줄러가 같은 시점에 같은 대상을 조회해 처리
        List<List<Long>> claimedByPod = raceOnPods(() -> {
            List<Long> claimed = new ArrayList<>();
            for (Long id : outboxStatusService.findRetryTargetIds(100)) {
                outboxStatusService.claimForRetry(id).ifPresent(e -> claimed.add(e.getOutboxId()));
            }
            return claimed;
        });

        // then
        List<Long> all = claimedByPod.stream().flatMap(List::stream).toList();
        assertThat(all).containsExactlyInAnyOrderElementsOf(outboxIds);
    }

    private <T> List<T> raceOnPods(Callable<T> pod) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(PODS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < PODS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return pod.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private int inTx(Callable<Integer> work) {
        return transactionTemplate.execute(status -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private Long saveOutbox(NotificationOutbox.Status status, int retryCount, LocalDateTime nextAttemptAt) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.STUDENT)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());
        memberIds.add(member.getId());
        Notification notification = notificationRepository.save(Notification.builder()
                .receiver(member)
                .type(NotificationType.CHAT)
                .title("title")
                .messagePreview("body")
                .deeplink("/chat/rooms/1")
                .build());
        return outboxRepository.save(NotificationOutbox.builder()
                .notification(notification)
                .status(status)
                .retryCount(retryCount)
                .nextAttemptAt(nextAttemptAt)
                .build()).getId();
    }
}
