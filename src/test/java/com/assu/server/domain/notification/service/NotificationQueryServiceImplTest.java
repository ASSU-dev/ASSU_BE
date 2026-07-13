package com.assu.server.domain.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationSetting;
import com.assu.server.domain.notification.entity.NotificationType;
import com.assu.server.domain.notification.repository.NotificationRepository;
import com.assu.server.domain.notification.repository.NotificationSettingRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceImplTest {

	@InjectMocks
	private NotificationQueryServiceImpl notificationQueryService;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private NotificationSettingRepository notificationSettingRepository;

	private static final Long MEMBER_ID = 1L;

	@Test
	@DisplayName("페이지 번호가 1 미만이면 PAGE_UNDER_ONE 예외가 발생한다")
	void getNotifications_PageUnderOne_ThrowsException() {
		// 1. Given & When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> notificationQueryService.getNotifications("all", 0, 10, MEMBER_ID));

		// 2. Then
		assertEquals(ErrorStatus.PAGE_UNDER_ONE, exception.getCode());
	}

	@Test
	@DisplayName("존재하지 않는 회원이 알림 목록을 조회하면 NO_SUCH_MEMBER 예외가 발생한다")
	void getNotifications_MemberNotFound_ThrowsException() {
		// 1. Given
		when(memberRepository.existsById(MEMBER_ID)).thenReturn(false);

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> notificationQueryService.getNotifications("all", 1, 10, MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
	}

	@Test
	@DisplayName("unread 상태로 조회하면 안읽은 알림만 조회하는 쿼리가 호출된다")
	void getNotifications_UnreadStatus_QueriesUnreadOnly() {
		// 1. Given
		when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

		Member receiver = mock(Member.class);
		Notification notification = Notification.builder()
			.id(10L).receiver(receiver).type(NotificationType.STAMP)
			.title("스탬프").messagePreview("미리보기").deeplink("/stamp").isRead(false)
			.build();
		when(notificationRepository.findByReceiverIdAndIsReadFalseAndTypeNot(
			eq(MEMBER_ID), eq(NotificationType.CHAT), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1));

		// 2. When
		Map<String, Object> result = notificationQueryService.getNotifications("unread", 1, 10, MEMBER_ID);

		// 3. Then
		assertEquals(1, result.get("page"));
		assertEquals(1L, result.get("totalElements"));
		verify(notificationRepository, never())
			.findByReceiverIdAndTypeNot(any(), any(), any(Pageable.class));
	}

	@Test
	@DisplayName("전체 상태로 조회하면 채팅을 제외한 모든 알림을 페이징하여 반환한다")
	void getNotifications_AllStatus_ReturnsPagedResult() {
		// 1. Given
		when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

		Member receiver = mock(Member.class);
		Notification notification = Notification.builder()
			.id(10L).receiver(receiver).type(NotificationType.ORDER)
			.title("주문 알림").messagePreview("3번 테이블에서 주문").deeplink("/order/7").isRead(true)
			.build();
		when(notificationRepository.findByReceiverIdAndTypeNot(
			eq(MEMBER_ID), eq(NotificationType.CHAT), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1));

		// 2. When
		Map<String, Object> result = notificationQueryService.getNotifications("all", 1, 10, MEMBER_ID);

		// 3. Then
		assertEquals(1L, result.get("totalElements"));
		assertEquals(1, result.get("totalPages"));
		assertEquals(10, result.get("size"));
	}

	@Test
	@DisplayName("존재하지 않는 회원이 알림 설정을 조회하면 NO_SUCH_MEMBER 예외가 발생한다")
	void loadSettings_MemberNotFound_ThrowsException() {
		// 1. Given
		when(memberRepository.findMemberById(MEMBER_ID)).thenReturn(Optional.empty());

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> notificationQueryService.loadSettings(MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
	}

	@Test
	@DisplayName("학생의 알림 설정은 STAMP만 노출되며 저장된 설정이 없으면 기본값 true다")
	void loadSettings_Student_DefaultsToEnabled() {
		// 1. Given
		Member student = mock(Member.class);
		when(student.getRole()).thenReturn(UserRole.STUDENT);
		when(memberRepository.findMemberById(MEMBER_ID)).thenReturn(Optional.of(student));
		when(notificationSettingRepository.findAllByMemberId(MEMBER_ID)).thenReturn(List.of());

		// 2. When
		var response = notificationQueryService.loadSettings(MEMBER_ID);

		// 3. Then
		assertEquals(Map.of("STAMP", true), response.settings());
	}

	@Test
	@DisplayName("저장된 설정이 있으면 기본값 대신 저장된 값이 반영된다")
	void loadSettings_SavedSetting_OverridesDefault() {
		// 1. Given (STAMP 알림을 꺼둔 학생)
		Member student = mock(Member.class);
		when(student.getRole()).thenReturn(UserRole.STUDENT);
		when(memberRepository.findMemberById(MEMBER_ID)).thenReturn(Optional.of(student));

		NotificationSetting stampOff = NotificationSetting.builder()
			.member(student).type(NotificationType.STAMP).enabled(false).build();
		when(notificationSettingRepository.findAllByMemberId(MEMBER_ID)).thenReturn(List.of(stampOff));

		// 2. When
		var response = notificationQueryService.loadSettings(MEMBER_ID);

		// 3. Then
		assertEquals(Map.of("STAMP", false), response.settings());
	}

	@Test
	@DisplayName("안읽은 알림 존재 여부를 그대로 반환한다")
	void hasUnread_ReturnsRepositoryResult() {
		// 1. Given
		when(notificationRepository.existsByReceiverIdAndIsReadFalseAndTypeNot(MEMBER_ID, NotificationType.CHAT))
			.thenReturn(true);

		// 2. When & Then
		assertTrue(notificationQueryService.hasUnread(MEMBER_ID));
	}
}
