package com.assu.server.domain.backoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackofficePushLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushTargetType targetType;

    private Long receiverId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    private String deepLink;

    @Column(nullable = false)
    private Long sentByMemberId;

    @Column(nullable = false)
    private int recipientCount;

    @Column(nullable = false)
    private LocalDateTime sentAt;
}