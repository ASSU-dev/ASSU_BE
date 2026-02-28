package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.entity.OutboxCreatedEvent;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
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
            
            log.debug("[OutboxRetry] Retrying outboxId={} retryCount={}", 
                outbox.getId(), outbox.getRetryCount());
                
        } catch (Exception e) {
            log.error("[OutboxRetry] Failed to retry outboxId={}", outbox.getId(), e);
        }
    }
}