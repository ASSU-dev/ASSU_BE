package com.assu.server.infra.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Counter outboxPublishSuccess;
    private final Counter outboxPublishFailure;
    private final Counter fcmSendSuccess;
    private final Counter fcmSendFailure;
    private final Counter fcmSendException;
    private final Counter outboxRetry;

    public NotificationMetrics(MeterRegistry registry) {
        this.outboxPublishSuccess = Counter.builder("notification.outbox.publish")
                .tag("result", "success")
                .register(registry);
        this.outboxPublishFailure = Counter.builder("notification.outbox.publish")
                .tag("result", "failure")
                .register(registry);
        this.fcmSendSuccess = Counter.builder("notification.fcm.send")
                .tag("result", "success")
                .register(registry);
        this.fcmSendFailure = Counter.builder("notification.fcm.send")
                .tag("result", "failure")
                .register(registry);
        this.fcmSendException = Counter.builder("notification.fcm.send")
                .tag("result", "exception")
                .register(registry);
        this.outboxRetry = Counter.builder("notification.outbox.retry")
                .register(registry);
    }

    public void incrementOutboxPublish(String result) {
        if ("success".equals(result)) {
            outboxPublishSuccess.increment();
        } else if ("failure".equals(result)) {
            outboxPublishFailure.increment();
        }
    }

    public void incrementFcmSend(String result) {
        if ("success".equals(result)) {
            fcmSendSuccess.increment();
        } else if ("failure".equals(result)) {
            fcmSendFailure.increment();
        } else if ("exception".equals(result)) {
            fcmSendException.increment();
        }
    }

    public void incrementOutboxRetry() {
        outboxRetry.increment();
    }
}