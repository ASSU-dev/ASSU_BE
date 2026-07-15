package com.assu.server.domain.notification.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.entity.*;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import com.assu.server.domain.notification.repository.NotificationRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationBackofficeServiceImpl implements NotificationBackofficeService {

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAndQueue(Long receiverId, String title, String body, String deepLink) {
        Member member = memberRepository.findMemberById(receiverId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        Notification notification = Notification.builder()
                .receiver(member)
                .type(NotificationType.BACKOFFICE)
                .refId(null)
                .title(title)
                .messagePreview(body)
                .deeplink(deepLink != null ? deepLink : "/backoffice")
                .build();
        notificationRepository.save(notification);

        NotificationOutbox outbox = NotificationOutbox.builder()
                .notification(notification)
                .status(NotificationOutbox.Status.PENDING)
                .retryCount(0)
                .build();
        outboxRepository.save(outbox);

        eventPublisher.publishEvent(new OutboxCreatedEvent(
                outbox.getId(),
                member.getId(),
                notification.getTitle(),
                notification.getMessagePreview(),
                NotificationType.BACKOFFICE.name(),
                null,
                notification.getDeeplink(),
                notification.getId()
        ));
    }
}