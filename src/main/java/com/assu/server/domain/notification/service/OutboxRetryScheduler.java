package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRetryScheduler {
    
    private final NotificationOutboxRepository outboxRepository;
    private final OutboxRetryProcessor retryProcessor;
    
    @Scheduled(fixedDelay = 30000)
    public void retryPendingNotifications() {
        try {
            List<NotificationOutbox> pendingOutboxes = outboxRepository.findByStatusAndRetryCountLessThan(
                NotificationOutbox.Status.PENDING, 3);
            
            if (!pendingOutboxes.isEmpty()) {
                log.info("[OutboxRetry] Found {} pending notifications to retry", pendingOutboxes.size());
                
                for (NotificationOutbox outbox : pendingOutboxes) {
                    retryProcessor.processRetry(outbox);
                }
            }
        } catch (Exception e) {
            log.error("[OutboxRetry] Scheduler error", e);
        }
    }
}