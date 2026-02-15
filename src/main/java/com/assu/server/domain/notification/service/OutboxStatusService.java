package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OutboxStatusService {
    private final NotificationOutboxRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispatched(Long id) {
        try {
            int updated = repo.markDispatchedById(id);
            if (updated == 0) {
                log.warn("[OutboxStatus] DISPATCHED failed - outbox not found or already processed: {}", id);
            } else {
                log.info("[OutboxStatus] DISPATCHED success outboxId={}", id);
            }
        } catch (Exception e) {
            log.error("[OutboxStatus] DISPATCHED error for outboxId={}", id, e);
        }
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

    @Transactional(readOnly = true)
    public boolean isAlreadySent(Long id) {
        try {
            return repo.existsByIdAndStatus(id, NotificationOutbox.Status.SENT);
        } catch (Exception e) {
            log.error("[OutboxStatus] isAlreadySent error for outboxId={}", id, e);
            return false; // 안전한 기본값
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

}
