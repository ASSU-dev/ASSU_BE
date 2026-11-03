package com.assu.server.domain.notification.service;

import com.assu.server.infra.messaging.ConditionalOnRabbitEnabled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnRabbitEnabled
public class OutboxRetryScheduler {

    private static final int BATCH_SIZE = 100;

    private final OutboxStatusService outboxStatus;
    private final OutboxAfterCommitPublisher publisher;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void retryDueOutboxes() {
        for (Long outboxId : outboxStatus.findRetryTargetIds(BATCH_SIZE)) {
            try {
                outboxStatus.claimForRetry(outboxId).ifPresent(publisher::publish);
            } catch (Exception e) {
                log.error("[OutboxRetry] retry failed outboxId={}", outboxId, e);
            }
        }
    }
}
