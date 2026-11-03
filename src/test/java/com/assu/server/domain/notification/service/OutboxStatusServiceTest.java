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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(CommonMockConfig.class)
class OutboxStatusServiceTest {

    @Autowired
    private OutboxStatusService outboxStatusService;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void claimForSending_whenCalledTwice_thenOnlyFirstSucceeds() {
        Long outboxId = saveOutbox(NotificationOutbox.Status.DISPATCHED, 0, null);

        assertThat(outboxStatusService.claimForSending(outboxId)).isTrue();
        assertThat(outboxStatusService.claimForSending(outboxId)).isFalse();
        assertThat(statusOf(outboxId)).isEqualTo(NotificationOutbox.Status.SENDING);
    }

    @Test
    void claimForRetry_whenCalledTwice_thenOnlyOneInstanceWins() {
        // given
        Long outboxId = saveOutbox(NotificationOutbox.Status.PENDING, 0, LocalDateTime.now().minusSeconds(1));

        // when
        boolean first = outboxStatusService.claimForRetry(outboxId).isPresent();
        boolean second = outboxStatusService.claimForRetry(outboxId).isPresent();

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        NotificationOutbox outbox = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void claimForRetry_whenRetryExhausted_thenMarksFailed() {
        Long outboxId = saveOutbox(NotificationOutbox.Status.SENDING, OutboxStatusService.MAX_RETRY,
                LocalDateTime.now().minusSeconds(1));

        assertThat(outboxStatusService.claimForRetry(outboxId)).isEmpty();
        assertThat(statusOf(outboxId)).isEqualTo(NotificationOutbox.Status.FAILED);
    }

    @Test
    void findRetryTargetIds_returnsOnlyDuePendingOrExpiredSending() {
        // given
        LocalDateTime past = LocalDateTime.now().minusSeconds(1);
        LocalDateTime future = LocalDateTime.now().plusMinutes(5);
        Long duePending = saveOutbox(NotificationOutbox.Status.PENDING, 0, past);
        Long legacyPending = saveOutbox(NotificationOutbox.Status.PENDING, 0, null);
        Long expiredSending = saveOutbox(NotificationOutbox.Status.SENDING, 0, past);
        saveOutbox(NotificationOutbox.Status.PENDING, 0, future);
        saveOutbox(NotificationOutbox.Status.SENDING, 0, future);
        saveOutbox(NotificationOutbox.Status.DISPATCHED, 0, past);
        saveOutbox(NotificationOutbox.Status.SENT, 0, past);

        // when & then
        assertThat(outboxStatusService.findRetryTargetIds(100))
                .containsExactlyInAnyOrder(duePending, legacyPending, expiredSending);
    }

    @Test
    void scheduleRetry_whenSending_thenPendingWithFutureAttempt() {
        Long outboxId = saveOutbox(NotificationOutbox.Status.SENDING, 0, LocalDateTime.now().plusMinutes(5));

        outboxStatusService.scheduleRetry(outboxId);

        NotificationOutbox outbox = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.Status.PENDING);
        assertThat(outbox.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(outboxStatusService.findRetryTargetIds(100)).doesNotContain(outboxId);
    }

    private Long saveOutbox(NotificationOutbox.Status status, int retryCount, LocalDateTime nextAttemptAt) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.STUDENT)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());
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

    private NotificationOutbox.Status statusOf(Long outboxId) {
        return outboxRepository.findById(outboxId).orElseThrow().getStatus();
    }
}
