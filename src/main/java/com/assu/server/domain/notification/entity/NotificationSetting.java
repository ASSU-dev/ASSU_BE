package com.assu.server.domain.notification.entity;

import com.assu.server.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "notification_setting",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "type"}))
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    // CHAT, PARTNER_SUGGESTION, PARTNER_PROPOSAL ...
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private com.assu.server.domain.notification.entity.enums.NotificationType type;

    @Column(nullable = false)
    private Boolean enabled;
}