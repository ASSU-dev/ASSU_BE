package com.assu.server.domain.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationOutbox;
import com.assu.server.domain.notification.entity.NotificationSetting;
import com.assu.server.domain.notification.entity.NotificationType;
import com.assu.server.domain.notification.entity.OutboxCreatedEvent;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import com.assu.server.domain.notification.repository.NotificationRepository;
import com.assu.server.domain.notification.repository.NotificationSettingRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceImplTest {

	@InjectMocks
	private NotificationCommandServiceImpl notificationCommandService;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationOutboxRepository outboxRepository;

	@Mock
	private NotificationSettingRepository notificationSettingRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private static final Long RECEIVER_ID = 1L;

	private Member givenMember() {
		Member member = mock(Member.class);
		lenient().when(member.getId()).thenReturn(RECEIVER_ID);
		when(memberRepository.findMemberById(RECEIVER_ID)).thenReturn(Optional.of(member));
		return member;
	}

	// ===== createAndQueue =====

	@Test
	@DisplayName("존재하지 않는 회원에게 알림을 생성하면 NO_SUCH_MEMBER 예외가 발생한다")
	void createAndQueue_MemberNotFound_ThrowsException() {
		// 1. Given
		when(memberRepository.findMemberById(RECEIVER_ID)).thenReturn(Optional.empty());

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> notificationCommandService.createAndQueue(RECEIVER_ID, NotificationType.CHAT, 5L,
				Map.of("senderName", "총학생회", "message", "안녕하세요")));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
		verify(notificationRepository, never()).save(any());
	}

	@Test
	@DisplayName("채팅 알림 생성 시 알림·Outbox가 저장되고 발행 이벤트가 발생한다")
	void createAndQueue_Success_SavesNotificationOutboxAndPublishesEvent() {
		// 1. Given
		givenMember();

		// 2. When
		Notification result = notificationCommandService.createAndQueue(
			RECEIVER_ID, NotificationType.CHAT, 5L,
			Map.of("senderName", "총학생회", "message", "안녕하세요"));

		// 3. Then (알림 내용 검증)
		assertEquals("새 메시지", result.getTitle());
		assertEquals("총학생회: 안녕하세요", result.getMessagePreview());
		assertEquals("/chat/5", result.getDeeplink());
		assertEquals(NotificationType.CHAT, result.getType());
		verify(notificationRepository, times(1)).save(result);

		// Outbox가 PENDING·재시도 0회로 저장되는지
		ArgumentCaptor<NotificationOutbox> outboxCaptor = ArgumentCaptor.forClass(NotificationOutbox.class);
		verify(outboxRepository, times(1)).save(outboxCaptor.capture());
		assertEquals(NotificationOutbox.Status.PENDING, outboxCaptor.getValue().getStatus());
		assertEquals(0, outboxCaptor.getValue().getRetryCount());

		verify(eventPublisher, times(1)).publishEvent(any(OutboxCreatedEvent.class));
	}

	@Test
	@DisplayName("refId가 없는 스탬프 알림은 딥링크가 타입 경로만으로 생성된다")
	void createAndQueue_NoRefId_DeeplinkWithoutId() {
		// 1. Given
		givenMember();

		// 2. When
		Notification result = notificationCommandService.createAndQueue(
			RECEIVER_ID, NotificationType.STAMP, null, Map.of());

		// 3. Then
		assertEquals("/stamp", result.getDeeplink());
		assertNull(result.getRefId());
	}

	// ===== markRead =====

	@Test
	@DisplayName("존재하지 않는 알림을 읽음 처리하면 NOTIFICATION_NOT_FOUND 예외가 발생한다")
	void markRead_NotificationNotFound_ThrowsException() {
		// 1. Given
		when(notificationRepository.findById(10L)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> notificationCommandService.markRead(10L, RECEIVER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NOTIFICATION_NOT_FOUND, exception.getCode());
	}

	@Test
	@DisplayName("다른 회원의 알림을 읽음 처리하면 NOTIFICATION_ACCESS_DENIED 예외가 발생한다")
	void markRead_NotOwner_ThrowsException() {
		// 1. Given
		Member owner = mock(Member.class);
		when(owner.getId()).thenReturn(999L);
		Notification notification = Notification.builder()
			.id(10L).receiver(owner).type(NotificationType.CHAT)
			.title("새 메시지").messagePreview("미리보기").deeplink("/chat/5").isRead(false)
			.build();
		when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> notificationCommandService.markRead(10L, RECEIVER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NOTIFICATION_ACCESS_DENIED, exception.getCode());
		assertFalse(notification.isRead());
	}

	@Test
	@DisplayName("본인의 알림을 읽음 처리하면 isRead가 true가 되고 읽은 시각이 기록된다")
	void markRead_Success_MarksAsRead() {
		// 1. Given
		Member owner = mock(Member.class);
		when(owner.getId()).thenReturn(RECEIVER_ID);
		Notification notification = Notification.builder()
			.id(10L).receiver(owner).type(NotificationType.CHAT)
			.title("새 메시지").messagePreview("미리보기").deeplink("/chat/5").isRead(false)
			.build();
		when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

		// 2. When
		notificationCommandService.markRead(10L, RECEIVER_ID);

		// 3. Then
		assertTrue(notification.isRead());
		assertNotNull(notification.getReadAt());
	}

	// ===== sendChat (설정에 따른 분기) =====

	@Test
	@DisplayName("알림 설정이 꺼져 있으면 알림만 저장하고 Outbox·이벤트는 생성하지 않는다")
	void sendChat_Disabled_SavesNotificationOnly() {
		// 1. Given
		Member member = givenMember();
		NotificationSetting disabled = NotificationSetting.builder()
			.member(member).type(NotificationType.CHAT).enabled(false).build();
		when(notificationSettingRepository.findByMemberIdAndType(RECEIVER_ID, NotificationType.PARTNER_ALL))
			.thenReturn(Optional.empty());
		when(notificationSettingRepository.findByMemberIdAndType(RECEIVER_ID, NotificationType.ADMIN_ALL))
			.thenReturn(Optional.empty());
		when(notificationSettingRepository.findByMemberIdAndType(RECEIVER_ID, NotificationType.CHAT))
			.thenReturn(Optional.of(disabled));

		// 2. When
		notificationCommandService.sendChat(RECEIVER_ID, 5L, "총학생회", "안녕하세요");

		// 3. Then
		verify(notificationRepository, times(1)).save(any(Notification.class));
		verify(outboxRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("알림 설정이 없으면 기본값(켜짐)으로 간주하여 Outbox까지 생성한다")
	void sendChat_NoSetting_DefaultsToEnabled() {
		// 1. Given
		givenMember();
		when(notificationSettingRepository.findByMemberIdAndType(RECEIVER_ID, NotificationType.PARTNER_ALL))
			.thenReturn(Optional.empty());
		when(notificationSettingRepository.findByMemberIdAndType(RECEIVER_ID, NotificationType.ADMIN_ALL))
			.thenReturn(Optional.empty());
		when(notificationSettingRepository.findByMemberIdAndType(RECEIVER_ID, NotificationType.CHAT))
			.thenReturn(Optional.empty());

		// 2. When
		notificationCommandService.sendChat(RECEIVER_ID, 5L, "총학생회", "안녕하세요");

		// 3. Then
		verify(notificationRepository, times(1)).save(any(Notification.class));
		verify(outboxRepository, times(1)).save(any(NotificationOutbox.class));
		verify(eventPublisher, times(1)).publishEvent(any(OutboxCreatedEvent.class));
	}

	// ===== toggle =====

	@Test
	@DisplayName("단일 타입 토글 시 기존 설정이 반전되어 저장된다")
	void toggle_SingleType_FlipsSetting() {
		// 1. Given (PARTNER 역할, CHAT 설정이 켜져 있는 상태)
		Member member = givenMember();
		when(member.getRole()).thenReturn(UserRole.PARTNER);

		NotificationSetting chatSetting = NotificationSetting.builder()
			.member(member).type(NotificationType.CHAT).enabled(true).build();
		when(notificationSettingRepository.findByMemberIdAndType(RECEIVER_ID, NotificationType.CHAT))
			.thenReturn(Optional.of(chatSetting));

		// 2. When
		notificationCommandService.toggle(RECEIVER_ID, NotificationType.CHAT);

		// 3. Then
		assertFalse(chatSetting.getEnabled());
		verify(notificationSettingRepository, times(1)).save(chatSetting);
	}

	@Test
	@DisplayName("PARTNER_ALL 토글 시 CHAT과 ORDER 설정이 함께 토글된다")
	void toggle_PartnerAll_TogglesChatAndOrder() {
		// 1. Given
		Member member = givenMember();
		when(member.getRole()).thenReturn(UserRole.PARTNER);
		when(notificationSettingRepository.findByMemberIdAndType(eq(RECEIVER_ID), any()))
			.thenReturn(Optional.empty());

		// 2. When
		notificationCommandService.toggle(RECEIVER_ID, NotificationType.PARTNER_ALL);

		// 3. Then (PARTNER_ALL 자체와 하위 CHAT·ORDER 타입까지 총 3건이 false로 새로 저장)
		ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
		verify(notificationSettingRepository, times(3)).save(captor.capture());

		var savedTypes = captor.getAllValues().stream().map(NotificationSetting::getType).toList();
		assertTrue(savedTypes.contains(NotificationType.CHAT));
		assertTrue(savedTypes.contains(NotificationType.ORDER));
		captor.getAllValues().forEach(s -> assertFalse(s.getEnabled()));
	}
}
