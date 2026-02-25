package com.assu.server.domain.notification.service;


import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.dto.QueueNotificationRequestDTO;
import com.assu.server.domain.notification.entity.*;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import com.assu.server.domain.notification.repository.NotificationRepository;
import com.assu.server.domain.notification.repository.NotificationSettingRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationCommandServiceImpl implements NotificationCommandService {
    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final MemberRepository memberRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Override
    public Notification createAndQueue(Long receiverId, NotificationType type, Long refId, Map<String, Object> ctx) {
        Member member = memberRepository.findMemberById(receiverId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        Notification notification = createNotification(member, type, refId, ctx);
        notificationRepository.save(notification);

        // Outbox 생성
        NotificationOutbox outbox = NotificationOutbox.builder()
                .notification(notification)
                .status(NotificationOutbox.Status.PENDING)
                .retryCount(0)
                .build();
        outboxRepository.save(outbox);

        // 이벤트 발행
        OutboxCreatedEvent event = new OutboxCreatedEvent(outbox.getId(), notification);
        eventPublisher.publishEvent(event);

        return notification;
    }

    @Override
    public void markRead(Long notificationId, Long currentMemberId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NOTIFICATION_NOT_FOUND));

        if (!n.getReceiver().getId().equals(currentMemberId)) {
            throw new DatabaseException(ErrorStatus.NOTIFICATION_ACCESS_DENIED);
        }
        n.markRead();
    }

    @Override
    public Map<String, Boolean> toggle(Long memberId, NotificationType type) {
        Member member = memberRepository.findMemberById(memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        if (type == NotificationType.PARTNER_ALL) {
            toggleSingle(member, NotificationType.CHAT);
            toggleSingle(member, NotificationType.ORDER);
        } else if (type == NotificationType.ADMIN_ALL) {
            toggleSingle(member, NotificationType.CHAT);
            toggleSingle(member, NotificationType.PARTNER_SUGGESTION);
            toggleSingle(member, NotificationType.PARTNER_PROPOSAL);
        } else {
            toggleSingle(member, type);
        }

        return buildToggleResult(memberId, member.getRole());
    }

    protected void sendIfEnabled(Long receiverId, NotificationType type, Long refId, Map<String, Object> ctx) {
        if (!isEnabled(receiverId, type)) {
            Member member = memberRepository.findMemberById(receiverId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));
            notificationRepository.save(createNotification(member, type, refId, ctx));
            return;
        }
        createAndQueue(receiverId, type, refId, ctx);
    }

    // 간단한 전송 메서드들
    @Override
    public void sendChat(Long receiverId, Long roomId, String senderName, String message) {
        sendIfEnabled(receiverId, NotificationType.CHAT, roomId, 
            Map.of("senderName", senderName, "message", message));
    }

    @Override
    public void sendOrder(Long receiverId, Long orderId, String tableNum, String paperContent) {
        sendIfEnabled(receiverId, NotificationType.ORDER, orderId,
            Map.of("table_num", tableNum, "paper_content", paperContent));
    }

    @Override
    public void sendPartnerSuggestion(Long receiverId, Long suggestionId) {
        sendIfEnabled(receiverId, NotificationType.PARTNER_SUGGESTION, suggestionId, Map.of());
    }

    @Override
    public void sendPartnerProposal(Long receiverId, Long proposalId, String partnerName) {
        sendIfEnabled(receiverId, NotificationType.PARTNER_PROPOSAL, proposalId,
            Map.of("partner_name", partnerName));
    }

    @Override
    public void sendStamp(Long receiverId) {
        sendIfEnabled(receiverId, NotificationType.STAMP, null, Map.of());
    }

    @Override
    public void queue(QueueNotificationRequestDTO req) {
        System.out.println("Queue called with type: " + req.type() + ", receiverId: " + req.receiverId());
        
        if (req.type() == null) {
            throw new DatabaseException(ErrorStatus.INVALID_NOTIFICATION_TYPE);
        }
        if (req.receiverId() == null) {
            throw new DatabaseException(ErrorStatus.MISSING_NOTIFICATION_FIELD);
        }

        final NotificationType type;
        try {
            type = NotificationType.valueOf(req.type().toUpperCase(Locale.ROOT));
            System.out.println("Parsed type: " + type);
        } catch (IllegalArgumentException e) {
            throw new DatabaseException(ErrorStatus.INVALID_NOTIFICATION_TYPE);
        }

        final Long receiverId = req.receiverId();

        switch (type) {
            case CHAT -> {
                Long roomId = (req.refId() != null) ? req.refId() : req.roomId();
                if (roomId == null || req.senderName() == null || req.message() == null) {
                    throw new DatabaseException(ErrorStatus.MISSING_NOTIFICATION_FIELD);
                }
                sendChat(receiverId, roomId, req.senderName(), req.message());
            }

            case PARTNER_SUGGESTION -> {
                Long suggestionId = (req.refId() != null) ? req.refId() : req.suggestionId();
                if (suggestionId == null) {
                    throw new DatabaseException(ErrorStatus.MISSING_NOTIFICATION_FIELD);
                }
                sendPartnerSuggestion(receiverId, suggestionId);
            }

            case ORDER -> {
                Long orderId = (req.refId() != null) ? req.refId() : req.orderId();
                if (orderId == null || req.table_num() == null || req.paper_content() == null) {
                    throw new DatabaseException(ErrorStatus.MISSING_NOTIFICATION_FIELD);
                }
                sendOrder(receiverId, orderId, req.table_num(), req.paper_content());
            }

            case PARTNER_PROPOSAL -> {
                Long proposalId = (req.refId() != null) ? req.refId() : req.proposalId();
                if (proposalId == null || req.partner_name() == null) {
                    throw new DatabaseException(ErrorStatus.MISSING_NOTIFICATION_FIELD);
                }
                sendPartnerProposal(receiverId, proposalId, req.partner_name());
            }

            case STAMP -> {
                System.out.println("Calling sendStamp for receiverId: " + receiverId);
                sendStamp(receiverId);
            }

            default -> throw new DatabaseException(ErrorStatus.INVALID_NOTIFICATION_TYPE);
        }
    }

    // Helper methods
    private Notification createNotification(Member member, NotificationType type, Long refId, Map<String, Object> ctx) {
        String deeplink = refId != null 
                ? "/" + type.name().toLowerCase() + "/" + refId
                : "/" + type.name().toLowerCase();
        
        return Notification.builder()
                .receiver(member)
                .type(type)
                .refId(refId)
                .title(getTitle(type))
                .messagePreview(getPreview(type, ctx))
                .deeplink(deeplink)
                .build();
    }

    private String getTitle(NotificationType type) {
        return switch(type) {
            case CHAT -> "새 메시지";
            case ORDER -> "주문 알림";
            case PARTNER_SUGGESTION -> "제휴 건의";
            case PARTNER_PROPOSAL -> "제휴 제안";
            case STAMP -> "스탬프 10개 달성! 이벤트 응모 완료 🎁";
            default -> "알림";
        };
    }

    private String getPreview(NotificationType type, Map<String, Object> ctx) {
        return switch(type) {
            case CHAT -> ctx.get("senderName") + ": " + ctx.get("message");
            case ORDER -> ctx.get("table_num") + "번 테이블에서 주문";
            case PARTNER_SUGGESTION -> "새로운 제휴 건의가 도착했습니다";
            case PARTNER_PROPOSAL -> ctx.get("partner_name") + "에서 제휴 제안";
            case STAMP -> "스탬프 10개가 모두 적립되어\n기프티콘 증정 이벤트에 자동으로 응모되었어요.";
            default -> "새로운 알림";
        };
    }

    @Override
    public boolean isEnabled(Long memberId, NotificationType type) {
        return notificationSettingRepository.findByMemberIdAndType(memberId, type)
                .map(ns -> Boolean.TRUE.equals(ns.getEnabled()))
                .orElse(true);
    }

    private boolean toggleSingle(Member member, NotificationType type) {
        NotificationSetting setting = notificationSettingRepository
                .findByMemberIdAndType(member.getId(), type)
                .orElse(NotificationSetting.builder()
                        .member(member)
                        .type(type)
                        .enabled(true)
                        .build());

        setting.setEnabled(!setting.getEnabled());
        notificationSettingRepository.save(setting);
        return setting.getEnabled();
    }

    private Map<String, Boolean> buildToggleResult(Long memberId, UserRole role) {
        EnumSet<NotificationType> visibleTypes = switch(role) {
            case ADMIN -> EnumSet.of(NotificationType.CHAT, NotificationType.PARTNER_SUGGESTION, NotificationType.PARTNER_PROPOSAL);
            case PARTNER -> EnumSet.of(NotificationType.CHAT, NotificationType.ORDER);
            case STUDENT -> EnumSet.of(NotificationType.STAMP);
        };

        Map<String, Boolean> result = new LinkedHashMap<>();
        visibleTypes.forEach(t -> result.put(t.name(), true));

        notificationSettingRepository.findAllByMemberId(memberId).forEach(s -> {
            if (visibleTypes.contains(s.getType())) {
                result.put(s.getType().name(), Boolean.TRUE.equals(s.getEnabled()));
            }
        });

        return result;
    }

}
