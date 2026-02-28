package com.assu.server.domain.notification.entity;

import lombok.Value;

@Value
public class OutboxCreatedEvent {
    Long outboxId;
    Long receiverId;
    String title;
    String messagePreview;
    String type;
    Long refId;
    String deeplink;
    Long notificationId;
}
