package com.assu.server.domain.notification.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name="notification_id", nullable=false, unique=true)
    private Notification notification;

    // 알림 전송 상태 (PENDING → DISPATCHED → SENDING → SENT/FAILED). 재시도 대기는 PENDING + nextAttemptAt
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private Status status;

    @NotNull
    @Column(nullable=false)
    private int retryCount;

    private LocalDateTime nextAttemptAt;

    public static final Duration PUBLISH_GRACE = Duration.ofMinutes(1);

    public static NotificationOutbox pending(Notification notification) {
        return NotificationOutbox.builder()
                .notification(notification)
                .status(Status.PENDING)
                .retryCount(0)
                .nextAttemptAt(LocalDateTime.now().plus(PUBLISH_GRACE))
                .build();
    }

    public enum Status { PENDING, SENDING, DISPATCHED, SENT, FAILED }
}
