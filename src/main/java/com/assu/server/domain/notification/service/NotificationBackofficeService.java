package com.assu.server.domain.notification.service;

public interface NotificationBackofficeService {
    void createAndQueue(Long receiverId, String title, String body, String deepLink);
}
