package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeOutboxResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficePushLogResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficePushSendRequestDTO;
import com.assu.server.domain.backoffice.entity.BackofficePushLog;
import com.assu.server.domain.backoffice.entity.PushTargetType;
import com.assu.server.domain.backoffice.repository.BackofficePushLogRepository;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.entity.OutboxCreatedEvent;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class BackofficeNotificationServiceImpl implements BackofficeNotificationService {

    private final NotificationCommandService notificationCommandService;
    private final NotificationOutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberRepository memberRepository;
    private final BackofficePushLogRepository pushLogRepository;

    @Override
    public void sendPush(BackofficePushSendRequestDTO request, Long sentByMemberId) {
        List<Member> recipients = resolveRecipients(request);

        for (Member recipient : recipients) {
            notificationCommandService.createAndQueue(
                    recipient.getId(),
                    request.title(),
                    request.body(),
                    request.deepLink()
            );
        }

        pushLogRepository.save(BackofficePushLog.builder()
                .targetType(request.targetType())
                .receiverId(request.targetType() == PushTargetType.INDIVIDUAL ? request.receiverId() : null)
                .title(request.title())
                .body(request.body())
                .deepLink(request.deepLink())
                .sentByMemberId(sentByMemberId)
                .recipientCount(recipients.size())
                .sentAt(LocalDateTime.now())
                .build());
    }

    private List<Member> resolveRecipients(BackofficePushSendRequestDTO request) {
        return switch (request.targetType()) {
            case INDIVIDUAL -> {
                if (request.receiverId() == null) {
                    throw new DatabaseException(ErrorStatus.NO_SUCH_MEMBER);
                }
                Member member = memberRepository.findMemberById(request.receiverId())
                        .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));
                yield List.of(member);
            }
            case ALL -> memberRepository.findAll();
            case STUDENT -> memberRepository.findByRole(UserRole.STUDENT);
            case UNION -> memberRepository.findByRole(UserRole.ADMIN);
            case PARTNER -> memberRepository.findByRole(UserRole.PARTNER);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<BackofficePushLogResponseDTO> getPushLogs(String keyword, int page, int size) {
        if (page < 1) throw new DatabaseException(ErrorStatus.PAGE_UNDER_ONE);
        if (size < 1 || size > 200) throw new DatabaseException(ErrorStatus.PAGE_SIZE_INVALID);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<BackofficePushLogResponseDTO> result;
        if (keyword != null && !keyword.isBlank()) {
            result = pushLogRepository
                    .findByTitleContainingOrBodyContaining(keyword, keyword, pageable)
                    .map(BackofficePushLogResponseDTO::from);
        } else {
            result = pushLogRepository.findAll(pageable).map(BackofficePushLogResponseDTO::from);
        }
        return PageResponseDTO.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<BackofficeOutboxResponseDTO> getFailedOutboxes(int page, int size) {
        if (page < 1) throw new DatabaseException(ErrorStatus.PAGE_UNDER_ONE);
        if (size < 1 || size > 200) throw new DatabaseException(ErrorStatus.PAGE_SIZE_INVALID);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<BackofficeOutboxResponseDTO> result = outboxRepository
                .findByStatus(NotificationOutbox.Status.FAILED, pageable)
                .map(BackofficeOutboxResponseDTO::from);
        return PageResponseDTO.of(result);
    }

    @Override
    public void retryOutbox(Long outboxId) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.OUTBOX_NOT_FOUND));

        if (outbox.getStatus() != NotificationOutbox.Status.FAILED) {
            throw new DatabaseException(ErrorStatus.OUTBOX_NOT_FAILED);
        }

        outboxRepository.resetToPendingById(outboxId);

        Notification n = outbox.getNotification();
        eventPublisher.publishEvent(new OutboxCreatedEvent(
                outbox.getId(),
                n.getReceiver().getId(),
                n.getTitle(),
                n.getMessagePreview(),
                n.getType().name(),
                n.getRefId(),
                n.getDeeplink(),
                n.getId()
        ));
    }
}