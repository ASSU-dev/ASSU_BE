package com.assu.server.domain.notification.repository;

import com.assu.server.domain.notification.entity.NotificationOutbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).DISPATCHED}
         WHERE o.id = :id
           AND o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).PENDING}
        """)
    int markDispatchedById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).SENDING},
               o.nextAttemptAt = :leaseUntil
         WHERE o.id = :id
           AND o.status IN (
               :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).PENDING},
               :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).DISPATCHED})
        """)
    int claimForSending(@Param("id") Long id, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).SENT}
         WHERE o.id = :id
           AND o.status <> :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).SENT}
        """)
    int markSentById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).FAILED}
         WHERE o.id = :id
           AND o.status <> :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).FAILED}
        """)
    int markFailedById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).PENDING},
               o.nextAttemptAt = :nextAttemptAt
         WHERE o.id = :id
           AND o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).SENDING}
        """)
    int scheduleRetryById(@Param("id") Long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    @Query("""
        SELECT o.id FROM NotificationOutbox o
         WHERE o.status IN (
               :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).PENDING},
               :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).SENDING})
           AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
         ORDER BY o.id
        """)
    List<Long> findRetryTargetIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).PENDING},
               o.retryCount = o.retryCount + 1,
               o.nextAttemptAt = :nextAttemptAt
         WHERE o.id = :id
           AND o.retryCount = :retryCount
           AND o.status IN (
               :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).PENDING},
               :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).SENDING})
           AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
        """)
    int claimForRetry(@Param("id") Long id,
                      @Param("retryCount") int retryCount,
                      @Param("now") LocalDateTime now,
                      @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).FAILED}
         WHERE o.id = :id
           AND o.status IN (
               :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).PENDING},
               :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).SENDING})
           AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
        """)
    int giveUpById(@Param("id") Long id, @Param("now") LocalDateTime now);

    Page<NotificationOutbox> findByStatus(NotificationOutbox.Status status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).PENDING},
               o.retryCount = 0,
               o.nextAttemptAt = :nextAttemptAt
         WHERE o.id = :id
           AND o.status = :#{T(com.assu.server.domain.notification.entity.NotificationOutbox$Status).FAILED}
        """)
    int resetToPendingById(@Param("id") Long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM NotificationOutbox o WHERE o.notification.receiver.id = :receiverId")
    void deleteAllByReceiverId(@Param("receiverId") Long receiverId);
}
