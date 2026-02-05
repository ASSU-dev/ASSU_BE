package com.assu.server.domain.notification.service;

import com.assu.server.infra.firebase.AmqpConfig;
import com.assu.server.infra.firebase.FcmClient;
import com.assu.server.domain.notification.dto.NotificationMessageDTO;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final FcmClient fcmClient;
    private final OutboxStatusService outboxStatus;

    @RabbitListener(queues = AmqpConfig.QUEUE, ackMode = "MANUAL")
    public void onMessage(@Payload NotificationMessageDTO notificationMessageDTO,
                          Channel ch,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {

        final Long outboxId = safeParseLong(notificationMessageDTO.idempotencyKey());

        try {
            if (isDuplicateMessage(outboxId)) {
                ch.basicAck(tag, false);
                return;
            }

            sendNotification(notificationMessageDTO, outboxId);
            ch.basicAck(tag, false);

        } catch (Exception e) {
            handleException(e, outboxId, notificationMessageDTO.receiverId());
            ch.basicNack(tag, false, false);
        }
    }

    private boolean isDuplicateMessage(Long outboxId) {
        if (outboxId != null && outboxStatus.isAlreadySent(outboxId)) {
            log.debug("[Notify] already-sent outboxId={}, ACK", outboxId);
            return true;
        }
        return false;
    }

    private void sendNotification(NotificationMessageDTO dto, Long outboxId) 
            throws FirebaseMessagingException, java.util.concurrent.TimeoutException, 
                   InterruptedException, java.util.concurrent.ExecutionException {
        FcmClient.FcmResult result = fcmClient.sendToMemberId(
                dto.receiverId(), dto.title(), dto.body(), dto.data());

        if (outboxId != null) outboxStatus.markSent(outboxId);

        log.info("[Notify] sent outboxId={} memberId={} success={} fail={} invalidTokens={}",
                outboxId, dto.receiverId(), result.successCount(), result.failureCount(), result.invalidTokens());
    }

    private void handleException(Exception e, Long outboxId, Long memberId) {
        if (outboxId != null) outboxStatus.markFailed(outboxId);

        if (e instanceof FirebaseMessagingException fme) {
            handleFcmException(fme, outboxId, memberId);
        } else if (e instanceof java.net.UnknownHostException || e instanceof javax.net.ssl.SSLHandshakeException) {
            log.error("[Notify] ENV failure outboxId={} memberId={} root={} [type=network]",
                    outboxId, memberId, rootSummary(e), e);
        } else if (e instanceof java.util.concurrent.TimeoutException || e instanceof java.net.SocketTimeoutException) {
            log.warn("[Notify] TIMEOUT failure outboxId={} memberId={} root={} [type=timeout]",
                    outboxId, memberId, rootSummary(e), e);
        } else {
            log.error("[Notify] UNKNOWN failure outboxId={} memberId={} root={} [type=unknown]",
                    outboxId, memberId, rootSummary(e), e);
        }
    }

    private void handleFcmException(FirebaseMessagingException fme, Long outboxId, Long memberId) {
        boolean permanent = isPermanent(fme);
        log.error("[Notify] FCM failure outboxId={} memberId={} root={} [permanent={} http={} code={}]",
                outboxId, memberId, rootSummary(fme),
                permanent, FcmClient.httpStatusOf(fme), fme.getMessagingErrorCode(), fme);
    }

    private boolean isPermanent(FirebaseMessagingException fme) {
        var code = fme.getMessagingErrorCode();
        Integer http = FcmClient.httpStatusOf(fme);
        if (code == com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED
                || code == com.google.firebase.messaging.MessagingErrorCode.INVALID_ARGUMENT) return true;
        if (http != null && (http == 401 || http == 403)) return true;
        return false;
    }

    private String rootSummary(Throwable t) {
        Throwable r = t; while (r.getCause() != null) r = r.getCause();
        return r.getClass().getName() + ": " + String.valueOf(r.getMessage());
    }

    private Long safeParseLong(String s) {
        try { return s == null ? null : Long.valueOf(s); } catch (Exception ignore) { return null; }
    }
}