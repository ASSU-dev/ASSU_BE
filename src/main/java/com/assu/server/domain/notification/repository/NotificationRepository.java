package com.assu.server.domain.notification.repository;

import com.assu.server.domain.deviceToken.entity.DeviceToken;
import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationSetting;
import com.assu.server.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByReceiverIdAndTypeNot(Long receiverId, NotificationType type, Pageable pageable);
    Page<Notification> findByReceiverIdAndIsReadFalseAndTypeNot(Long receiverId, NotificationType type, Pageable pageable);
    boolean existsByReceiverIdAndIsReadFalseAndTypeNot(Long receiverId, NotificationType type);
}
