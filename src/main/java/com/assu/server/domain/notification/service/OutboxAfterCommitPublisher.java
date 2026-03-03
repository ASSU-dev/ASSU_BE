package com.assu.server.domain.notification.service;


import com.assu.server.domain.notification.dto.NotificationMessageDTO;
import com.assu.server.domain.notification.entity.OutboxCreatedEvent;
import com.assu.server.domain.notification.event.NotificationFailedEvent;
import com.assu.server.infra.firebase.AmqpConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxAfterCommitPublisher {
    private final RabbitTemplate rabbit;
    private final OutboxStatusService outboxStatus;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxCreated(OutboxCreatedEvent e) {
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
        } catch (Exception ex) {
            log.error("[Outbox] Failed to send message for outboxId={}", e.getOutboxId(), ex);
            // 큐 전송 실패 시 재시도 이벤트 발행
            eventPublisher.publishEvent(new NotificationFailedEvent(e.getOutboxId(), 0));
        }
    }
}
