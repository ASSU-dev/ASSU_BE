package com.assu.server.domain.notification.service;


import com.assu.server.domain.notification.dto.NotificationMessageDTO;
import com.assu.server.domain.notification.entity.OutboxCreatedEvent;
import com.assu.server.infra.firebase.AmqpConfig;
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
public class OutboxAfterCommitPublisher {
    private final RabbitTemplate rabbit;
    private final OutboxStatusService outboxStatus;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxCreated(OutboxCreatedEvent e) {
        var n = e.getNotification();

        var dto = new NotificationMessageDTO(
                String.valueOf(e.getOutboxId()),
                n.getReceiver().getId(),
                n.getTitle(),
                n.getMessagePreview(),
                Map.of(
                        "type", n.getType().name(),
                        "refId", String.valueOf(n.getRefId()),
                        "deeplink", n.getDeeplink() == null ? "" : n.getDeeplink(),
                        "notificationId", String.valueOf(n.getId())
                )
        );

        try {
            rabbit.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.ROUTING_KEY, dto);
            log.info("[Outbox] Message sent to queue for outboxId={}", e.getOutboxId());
            // 메시지 전송 성공 시에만 상태 변경
            outboxStatus.markDispatched(e.getOutboxId());
        } catch (Exception ex) {
            log.error("[Outbox] Failed to send message for outboxId={}", e.getOutboxId(), ex);
            // 전송 실패 시 상태는 PENDING으로 유지 (재시도 가능)
            // 예외를 다시 던지지 않음으로써 트랜잭션 롤백을 방지
        }
    }
}
