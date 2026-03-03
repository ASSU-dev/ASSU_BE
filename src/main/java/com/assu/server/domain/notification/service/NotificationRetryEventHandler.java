package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.event.NotificationFailedEvent;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryEventHandler {
    
    private final NotificationOutboxRepository outboxRepository;
    private final OutboxRetryProcessor retryProcessor;
    
    @EventListener
    @Async
    public void handleNotificationFailed(NotificationFailedEvent event) {
        if (event.getRetryCount() >= 3) {
            log.warn("[NotificationRetry] Max retry count reached for outbox: {}", event.getOutboxId());
            return;
        }
        
        // Exponential backoff: 30초, 60초, 120초
        long delaySeconds = 30L * (1L << event.getRetryCount());
        
        log.info("[NotificationRetry] Scheduling retry for outbox: {} in {} seconds", 
                event.getOutboxId(), delaySeconds);
        
        Executors.newSingleThreadScheduledExecutor()
            .schedule(() -> {
                try {
                    NotificationOutbox outbox = outboxRepository.findById(event.getOutboxId())
                        .orElse(null);
                    if (outbox != null && outbox.getStatus() == NotificationOutbox.Status.PENDING) {
                        retryProcessor.processRetry(outbox);
                    }
                } catch (Exception e) {
                    log.error("[NotificationRetry] Error during retry for outbox: {}", event.getOutboxId(), e);
                }
            }, delaySeconds, TimeUnit.SECONDS);
    }
}