package com.assu.server.domain.notification.service;


import com.assu.server.domain.notification.dto.NotificationMessageDTO;
import com.assu.server.domain.notification.entity.OutboxCreatedEvent;
import com.assu.server.infra.messaging.AmqpConfig;
import com.assu.server.infra.messaging.ConditionalOnRabbitEnabled;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnRabbitEnabled
public class OutboxAfterCommitPublisher {
    private final RabbitTemplate rabbit;
    private final OutboxStatusService outboxStatus;
    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxCreated(OutboxCreatedEvent e) {
        publish(e);
    }

    // 실패해도 outbox는 PENDING으로 남고, nextAttemptAt이 지나면 OutboxRetryScheduler가 다시 발행한다.
    public void publish(OutboxCreatedEvent e) {
        var dto = new NotificationMessageDTO(
                String.valueOf(e.getOutboxId()),
                e.getReceiverId(),
                e.getTitle(),
                e.getMessagePreview(),
                Map.of(
                        "type", e.getType(),
                        "refId", e.getRefId() != null ? String.valueOf(e.getRefId()) : "",
                        "deeplink", e.getDeeplink() == null ? "" : e.getDeeplink(),
                        "notificationId", String.valueOf(e.getNotificationId())
                )
        );

        try {
            rabbit.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.ROUTING_KEY, dto);
            log.info("[Outbox] Message sent to queue for outboxId={}", e.getOutboxId());
            outboxStatus.markDispatched(e.getOutboxId());
            meterRegistry.counter("notification.outbox.publish", "result", "success").increment();
        } catch (Exception ex) {
            log.error("[Outbox] Failed to send message for outboxId={}", e.getOutboxId(), ex);
            meterRegistry.counter("notification.outbox.publish", "result", "failure").increment();
        }
    }
}
