package com.assu.server.domain.notification.repository;

import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.entity.NotificationOutbox.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    boolean existsByIdAndStatus(Long id, NotificationOutbox.Status status);

    List<NotificationOutbox> findByStatusAndRetryCountLessThan(NotificationOutbox.Status status, int maxRetryCount);
}
