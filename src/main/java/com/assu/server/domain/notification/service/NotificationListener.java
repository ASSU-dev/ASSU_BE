package com.assu.server.domain.notification.service;

import com.assu.server.infra.firebase.ConditionalOnFirebaseEnabled;
import com.assu.server.infra.firebase.FcmClient;
import com.assu.server.infra.messaging.AmqpConfig;
import com.assu.server.infra.messaging.ConditionalOnRabbitEnabled;
import com.assu.server.domain.notification.dto.NotificationMessageDTO;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 큐 소비(Rabbit)와 실제 발송(FCM/Firebase) 양쪽에 의존하므로 두 플래그가 모두 켜져 있어야 등록된다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnRabbitEnabled
@ConditionalOnFirebaseEnabled
public class NotificationListener {

    private static final int MARK_SENT_ATTEMPTS = 3;
    private static final long MARK_SENT_BACKOFF_MILLIS = 200;
    private static final int DEFERRED_MARK_SENT_ATTEMPTS = 8;

    private final ScheduledExecutorService deferredMarkSentExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "outbox-mark-sent");
        t.setDaemon(true);
        return t;
    });
    private long deferredMarkSentDelayMillis = 30_000;

    private final FcmClient fcmClient;
    private final OutboxStatusService outboxStatus;
    private final MeterRegistry meterRegistry;

    @RabbitListener(queues = AmqpConfig.QUEUE, ackMode = "MANUAL")
    public void onMessage(@Payload NotificationMessageDTO notificationMessageDTO,
                          Channel ch,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {

        final Long outboxId = safeParseLong(notificationMessageDTO.idempotencyKey());

        try {
            if (!claimForSending(outboxId)) {
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

    private boolean claimForSending(Long outboxId) {
        if (outboxId == null) return true;
        try {
            if (outboxStatus.claimForSending(outboxId)) return true;
        } catch (Exception e) {
            // 선점 여부를 모른 채 보내면 이미 보낸 알림이 또 나갈 수 있어 보내지 않는다. 행은 재시도 스케줄러가 다시 발행한다
            log.error("[Notify] claim error, skip and leave to retry scheduler outboxId={}", outboxId, e);
            meterRegistry.counter("notification.outbox.status", "result", "claim_error").increment();
            return false;
        }
        log.info("[Notify] already sending or sent outboxId={}, ACK", outboxId);
        return false;
    }

    private void sendNotification(NotificationMessageDTO dto, Long outboxId) 
            throws FirebaseMessagingException, java.util.concurrent.TimeoutException, 
                   InterruptedException, java.util.concurrent.ExecutionException {
        FcmClient.FcmResult result = fcmClient.sendToMemberId(
                dto.receiverId(), dto.title(), dto.body(), dto.data());

        if (outboxId != null) {
            if (result.shouldRetry()) {
                log.warn("[Notify] all tokens failed with retryable errors, retry outboxId={} memberId={}", outboxId, dto.receiverId());
                outboxStatus.scheduleRetry(outboxId);
            } else {
                recordSent(outboxId);
            }
        }

        meterRegistry.counter("notification.fcm.send", "result", "success").increment(result.successCount());
        if (result.failureCount() > 0) {
            meterRegistry.counter("notification.fcm.send", "result", "failure").increment(result.failureCount());
        }

        log.info("[Notify] sent outboxId={} memberId={} success={} fail={} invalidTokens={}",
                outboxId, dto.receiverId(), result.successCount(), result.failureCount(), result.invalidTokens());
    }

    // 발송은 이미 끝났으므로 SENT 기록 실패를 발송 실패로 처리하지 않는다(FAILED로 남으면 수동 재전송 때 중복 발송된다)
    private void recordSent(Long outboxId) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MARK_SENT_ATTEMPTS; attempt++) {
            try {
                outboxStatus.markSent(outboxId);
                return;
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt < MARK_SENT_ATTEMPTS && !sleepQuietly(MARK_SENT_BACKOFF_MILLIS)) {
                    break;
                }
            }
        }
        meterRegistry.counter("notification.outbox.status", "result", "mark_sent_failed").increment();
        log.error("[Notify] delivered but SENT update failed, retrying in background outboxId={}", outboxId, lastError);
        scheduleDeferredMarkSent(outboxId, 1);
    }

    // SENDING 리스 만료 전에 SENT를 기록하지 못하면 재시도 스케줄러가 같은 알림을 다시 보내므로, 리스 안에서 계속 기록을 시도한다
    private void scheduleDeferredMarkSent(Long outboxId, int attempt) {
        if (attempt > DEFERRED_MARK_SENT_ATTEMPTS) {
            meterRegistry.counter("notification.outbox.status", "result", "mark_sent_gave_up").increment();
            log.error("[Notify] SENT update gave up, may be resent after lease outboxId={}", outboxId);
            return;
        }
        try {
            deferredMarkSentExecutor.schedule(() -> {
                try {
                    outboxStatus.markSent(outboxId);
                } catch (RuntimeException e) {
                    scheduleDeferredMarkSent(outboxId, attempt + 1);
                }
            }, deferredMarkSentDelayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.error("[Notify] deferred SENT update rejected outboxId={}", outboxId);
        }
    }

    @PreDestroy
    void shutdownDeferredMarkSent() {
        deferredMarkSentExecutor.shutdown();
    }

    private boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void handleException(Exception e, Long outboxId, Long memberId) {
        meterRegistry.counter("notification.fcm.send", "result", "exception").increment();
        if (outboxId != null) {
            if (isRetryable(e)) {
                outboxStatus.scheduleRetry(outboxId);
            } else {
                outboxStatus.markFailed(outboxId);
            }
        }

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

    private boolean isRetryable(Exception e) {
        if (e instanceof FirebaseMessagingException fme) {
            return !isPermanent(fme);
        }
        // 네트워크, 타임아웃 등은 재시도 가능
        return e instanceof java.net.UnknownHostException ||
               e instanceof javax.net.ssl.SSLHandshakeException ||
               e instanceof java.util.concurrent.TimeoutException ||
               e instanceof java.net.SocketTimeoutException;
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