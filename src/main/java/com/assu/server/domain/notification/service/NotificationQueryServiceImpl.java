package com.assu.server.domain.notification.service;

import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.converter.NotificationConverter;
import com.assu.server.domain.notification.dto.NotificationResponseDTO;
import com.assu.server.domain.notification.dto.NotificationSettingsResponseDTO;
import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationType;
import com.assu.server.domain.notification.repository.NotificationRepository;
import com.assu.server.domain.notification.repository.NotificationSettingRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {
    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    @Override
    public Map<String, Object> getNotifications(String status, int page, int size, Long memberId) {
        validateParams(page, size, memberId);
        
        boolean unreadOnly = "unread".equalsIgnoreCase(status);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        
        Page<Notification> rawPage = unreadOnly
                ? notificationRepository.findByReceiverIdAndIsReadFalseAndTypeNot(memberId, NotificationType.CHAT, pageable)
                : notificationRepository.findByReceiverIdAndTypeNot(memberId, NotificationType.CHAT, pageable);

        Page<NotificationResponseDTO> p = rawPage.map(NotificationConverter::toDto);

        return Map.of(
            "items", p.getContent(),
            "page", p.getNumber() + 1,
            "size", p.getSize(),
            "totalPages", p.getTotalPages(),
            "totalElements", p.getTotalElements()
        );
    }

    @Override
    public NotificationSettingsResponseDTO loadSettings(Long memberId) {
        Member member = memberRepository.findMemberById(memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        Set<NotificationType> visible = getVisibleTypes(member.getRole());
        Map<String, Boolean> settings = buildSettings(memberId, visible);
        
        return new NotificationSettingsResponseDTO(settings);
    }

    @Override
    public boolean hasUnread(Long memberId) {
        return notificationRepository.existsByReceiverIdAndIsReadFalseAndTypeNot(memberId, NotificationType.CHAT);
    }

    private void validateParams(int page, int size, Long memberId) {
        if (page < 1) throw new DatabaseException(ErrorStatus.PAGE_UNDER_ONE);
        if (size < 1 || size > 200) throw new DatabaseException(ErrorStatus.PAGE_SIZE_INVALID);
        if (!memberRepository.existsById(memberId)) throw new DatabaseException(ErrorStatus.NO_SUCH_MEMBER);
    }

    private Set<NotificationType> getVisibleTypes(UserRole role) {
        return role == UserRole.ADMIN
                ? EnumSet.of(NotificationType.CHAT, NotificationType.PARTNER_SUGGESTION, NotificationType.PARTNER_PROPOSAL)
                : EnumSet.of(NotificationType.CHAT, NotificationType.ORDER);
    }

    private Map<String, Boolean> buildSettings(Long memberId, Set<NotificationType> visible) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        visible.forEach(t -> map.put(t.name(), true));
        
        notificationSettingRepository.findAllByMemberId(memberId).forEach(s -> {
            if (visible.contains(s.getType())) {
                map.put(s.getType().name(), Boolean.TRUE.equals(s.getEnabled()));
            }
        });
        
        return map;
    }
}
