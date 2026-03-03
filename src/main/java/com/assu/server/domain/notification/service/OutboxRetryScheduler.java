package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.event.NotificationFailedEvent;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRetryScheduler {
    
    private final NotificationOutboxRepository outboxRepository;
    private final OutboxRetryProcessor retryProcessor;
    private final ApplicationEventPublisher eventPublisher;

    // 초기 시작 시 PENDING 상태인 알림들을 이벤트로 처리
    @EventListener(ApplicationReadyEvent.class)
    public void handleApplicationReady() {
        try {
            List<NotificationOutbox> pendingOutboxes = outboxRepository.findByStatusAndRetryCountLessThan(
                NotificationOutbox.Status.PENDING, 3);
            
            for (NotificationOutbox outbox : pendingOutboxes) {
                eventPublisher.publishEvent(new NotificationFailedEvent(outbox.getId(), outbox.getRetryCount()));
            }
            
            if (!pendingOutboxes.isEmpty()) {
                log.info("[OutboxRetry] Scheduled {} pending notifications on startup", pendingOutboxes.size());
            }
        } catch (Exception e) {
            log.error("[OutboxRetry] Error handling startup pending notifications", e);
        }
    }
}