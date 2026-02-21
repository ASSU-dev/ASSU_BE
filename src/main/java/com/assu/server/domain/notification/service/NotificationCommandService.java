package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.dto.QueueNotificationRequestDTO;
import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationType;

import java.nio.file.AccessDeniedException;
import java.util.Map;

public interface NotificationCommandService {
    Notification createAndQueue(Long receiverId, NotificationType type, Long refId, Map<String, Object> ctx);
    void markRead(Long notificationId, Long currentMemberId) throws AccessDeniedException;
    void queue(QueueNotificationRequestDTO req);
    Map<String, Boolean> toggle(Long memberId, NotificationType type);
    boolean isEnabled(Long memberId, NotificationType type);

    void sendChat(Long receiverId, Long roomId, String senderName, String message);
    void sendPartnerSuggestion(Long receiverId, Long suggestionId);
    void sendOrder(Long receiverId, Long orderId, String tableNum, String paperContent);
    void sendPartnerProposal(Long receiverId, Long proposalId, String partnerName);
    void sendStamp(Long receiverId);
}
