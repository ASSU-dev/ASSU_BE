package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.dto.NotificationSettingsResponseDTO;

import java.util.Map;

public interface NotificationQueryService {
    Map<String, Object> getNotifications(String status, int page, int size, Long memberId);
    NotificationSettingsResponseDTO loadSettings(Long memberId);
    boolean hasUnread(Long memberId);
}
