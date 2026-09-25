package com.assu.server.domain.notification.repository;

import com.assu.server.domain.notification.entity.NotificationSetting;
import com.assu.server.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByMemberIdAndType(Long memberId, NotificationType type);
    List<NotificationSetting> findAllByMemberId(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM NotificationSetting s WHERE s.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
