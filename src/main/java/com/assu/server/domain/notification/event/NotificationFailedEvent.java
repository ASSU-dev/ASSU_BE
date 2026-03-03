package com.assu.server.domain.notification.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationFailedEvent {
    private final Long outboxId;
    private final int retryCount;
}