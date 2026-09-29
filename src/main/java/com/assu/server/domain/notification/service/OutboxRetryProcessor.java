package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.entity.OutboxCreatedEvent;
import com.assu.server.domain.notification.event.NotificationFailedEvent;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import com.assu.server.infra.messaging.NotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class OutboxRetryProcessor {

    private final NotificationOutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationMetrics notificationMetrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRetry(NotificationOutbox outbox) {
        try {
            outbox.incrementRetryCount();
            outboxRepository.save(outbox);

            var n = outbox.getNotification();
            OutboxCreatedEvent event = new OutboxCreatedEvent(
                    outbox.getId(),
                    n.getReceiver().getId(),
                    n.getTitle(),
                    n.getMessagePreview(),
                    n.getType().name(),
                    n.getRefId(),
                    n.getDeeplink(),
                    n.getId()
            );
            eventPublisher.publishEvent(event);
            notificationMetrics.incrementOutboxRetry();

            log.debug("[OutboxRetry] Retrying outboxId={} retryCount={}",
                outbox.getId(), outbox.getRetryCount());

        } catch (Exception e) {
            log.error("[OutboxRetry] Failed to retry outboxId={}", outbox.getId(), e);

            if (outbox.getRetryCount() < 3) {
                eventPublisher.publishEvent(new NotificationFailedEvent(outbox.getId(), outbox.getRetryCount()));
            }
        }
    }
}
