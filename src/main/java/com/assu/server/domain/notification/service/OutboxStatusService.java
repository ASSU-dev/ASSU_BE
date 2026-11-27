package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.entity.OutboxCreatedEvent;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OutboxStatusService {

    public static final int MAX_RETRY = 3;
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration SENDING_LEASE = Duration.ofMinutes(5);
    // 대량 발송으로 큐가 밀려도 정상 소비 전에 재발행되지 않도록 큐 적체 시간보다 넉넉하게 둔다
    private static final Duration DISPATCH_TIMEOUT = Duration.ofMinutes(30);
    // 기한이 이보다 오래 지난 DISPATCHED는 배포 전부터 멈춰 있던 행으로 보고, 오래된 알림이 뒤늦게 나가지 않도록 자동 재시도하지 않는다
    private static final Duration DISPATCH_STALE_HORIZON = Duration.ofHours(1);

    private final NotificationOutboxRepository repo;
    private final MeterRegistry meterRegistry;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispatched(Long id) {
        try {
            int updated = repo.markDispatchedById(id, LocalDateTime.now().plus(DISPATCH_TIMEOUT));
            if (updated == 0) {
                log.warn("[OutboxStatus] DISPATCHED failed - outbox not found or already processed: {}", id);
            } else {
                log.info("[OutboxStatus] DISPATCHED success outboxId={}", id);
            }
        } catch (Exception e) {
            log.error("[OutboxStatus] DISPATCHED error for outboxId={}", id, e);
        }
    }

    // 발송 직전 선점. 이미 다른 소비자가 보내는 중이거나 끝난 알림이면 false.
    // 선점한 파드가 발송 중에 죽으면 SENDING_LEASE 뒤 재시도 대상이 된다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimForSending(Long id) {
        return repo.claimForSending(id, LocalDateTime.now().plus(SENDING_LEASE)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id) {
        try {
            int updated = repo.markSentById(id);
            if (updated == 0) {
                log.warn("[OutboxStatus] SENT failed - no rows updated for outboxId={}", id);
            } else {
                log.info("[OutboxStatus] SENT updated={} outboxId={}", updated, id);
            }
        } catch (Exception e) {
            log.error("[OutboxStatus] SENT error for outboxId={}", id, e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id) {
        try {
            int updated = repo.markFailedById(id);
            if (updated == 0) {
                log.warn("[OutboxStatus] FAILED failed - no rows updated for outboxId={}", id);
            } else {
                log.info("[OutboxStatus] FAILED updated={} outboxId={}", updated, id);
            }
        } catch (Exception e) {
            log.error("[OutboxStatus] FAILED error for outboxId={}", id, e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleRetry(Long id) {
        int retryCount = repo.findById(id).map(NotificationOutbox::getRetryCount).orElse(0);
        int updated = repo.scheduleRetryById(id, LocalDateTime.now().plus(retryDelay(retryCount)));
        log.info("[OutboxStatus] RETRY scheduled outboxId={} retryCount={} updated={}", id, retryCount, updated);
    }

    @Transactional(readOnly = true)
    public List<Long> findRetryTargetIds(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return repo.findRetryTargetIds(now, now.minus(DISPATCH_STALE_HORIZON), PageRequest.of(0, limit));
    }

    // 재시도 선점. retryCount 조건부 UPDATE라 여러 파드가 동시에 시도해도 한 파드만 성공한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OutboxCreatedEvent> claimForRetry(Long id) {
        NotificationOutbox outbox = repo.findById(id).orElse(null);
        if (outbox == null) return Optional.empty();

        LocalDateTime now = LocalDateTime.now();
        int retryCount = outbox.getRetryCount();
        if (retryCount >= MAX_RETRY) {
            if (repo.giveUpById(id, now, now.minus(DISPATCH_STALE_HORIZON)) > 0) {
                meterRegistry.counter("notification.outbox.retry", "result", "exhausted").increment();
                log.warn("[OutboxStatus] retry exhausted outboxId={} retryCount={}", id, retryCount);
            }
            return Optional.empty();
        }

        OutboxCreatedEvent event = toEvent(outbox);
        int claimed = repo.claimForRetry(id, retryCount, now, now.plus(retryDelay(retryCount + 1)),
                now.minus(DISPATCH_STALE_HORIZON));
        if (claimed == 0) return Optional.empty();

        meterRegistry.counter("notification.outbox.retry", "result", "claimed").increment();
        log.info("[OutboxStatus] retry claimed outboxId={} retryCount={}", id, retryCount + 1);
        return Optional.of(event);
    }

    private OutboxCreatedEvent toEvent(NotificationOutbox outbox) {
        Notification n = outbox.getNotification();
        return new OutboxCreatedEvent(
                outbox.getId(),
                n.getReceiver().getId(),
                n.getTitle(),
                n.getMessagePreview(),
                n.getType().name(),
                n.getRefId(),
                n.getDeeplink(),
                n.getId()
        );
    }

    private Duration retryDelay(int retryCount) {
        return BASE_RETRY_DELAY.multipliedBy(1L << Math.min(retryCount, MAX_RETRY));
    }
}
