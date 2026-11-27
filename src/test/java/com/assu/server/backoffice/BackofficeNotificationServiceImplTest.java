package com.assu.server.backoffice;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.assu.server.domain.backoffice.dto.BackofficePushSendRequestDTO;
import com.assu.server.domain.backoffice.entity.BackofficePushLog;
import com.assu.server.domain.backoffice.entity.PushTargetType;
import com.assu.server.domain.backoffice.repository.BackofficePushLogRepository;
import com.assu.server.domain.backoffice.service.BackofficeNotificationServiceImpl;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import com.assu.server.domain.notification.service.NotificationBackofficeService;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.global.util.DuplicateRequestGuard;
import com.assu.server.support.InMemoryDuplicateRequestGuard;

@ExtendWith(MockitoExtension.class)
class BackofficeNotificationServiceImplTest {

	private static final Long OPERATOR_ID = 1L;

	@InjectMocks
	private BackofficeNotificationServiceImpl backofficeNotificationService;

	@Mock
	private NotificationBackofficeService notificationBackofficeService;

	@Mock
	private NotificationOutboxRepository outboxRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private BackofficePushLogRepository pushLogRepository;

	@Spy
	private DuplicateRequestGuard duplicateRequestGuard = InMemoryDuplicateRequestGuard.create();

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void sendPush_whenSamePushSentTwice_thenQueuesAndLogsOnlyOnce() {
		// given
		BackofficePushSendRequestDTO request =
			new BackofficePushSendRequestDTO(PushTargetType.ALL, null, "공지사항", "안녕하세요", "/notice/1");
		when(memberRepository.findAllIds()).thenReturn(List.of(10L, 11L));
		backofficeNotificationService.sendPush(request, OPERATOR_ID);

		// when & then
		assertThatThrownBy(() -> backofficeNotificationService.sendPush(request, OPERATOR_ID))
			.isInstanceOf(GeneralException.class)
			.extracting("code")
			.isEqualTo(ErrorStatus.DUPLICATE_REQUEST);
		verify(notificationBackofficeService, times(2)).createAndQueue(anyLong(), anyString(), anyString(), anyString());
		verify(pushLogRepository, times(1)).save(any(BackofficePushLog.class));
	}

	@Test
	void sendPush_whenDifferentContent_thenSendsBoth() {
		// given
		when(memberRepository.findAllIds()).thenReturn(List.of(10L));

		// when
		backofficeNotificationService.sendPush(
			new BackofficePushSendRequestDTO(PushTargetType.ALL, null, "공지사항", "첫 번째", null), OPERATOR_ID);
		backofficeNotificationService.sendPush(
			new BackofficePushSendRequestDTO(PushTargetType.ALL, null, "공지사항", "두 번째", null), OPERATOR_ID);

		// then
		verify(pushLogRepository, times(2)).save(any(BackofficePushLog.class));
	}

	@Test
	void sendPush_whenFirstAttemptRolledBack_thenRetryIsAllowed() {
		// given
		BackofficePushSendRequestDTO request =
			new BackofficePushSendRequestDTO(PushTargetType.INDIVIDUAL, 42L, "공지사항", "안녕하세요", null);
		when(memberRepository.findMemberById(42L))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(mock(Member.class)));
		TransactionSynchronizationManager.initSynchronization();
		assertThatThrownBy(() -> backofficeNotificationService.sendPush(request, OPERATOR_ID))
			.isInstanceOf(DatabaseException.class);
		TransactionSynchronizationManager.getSynchronizations()
			.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		TransactionSynchronizationManager.clearSynchronization();

		// when & then
		assertThatCode(() -> backofficeNotificationService.sendPush(request, OPERATOR_ID)).doesNotThrowAnyException();
		verify(pushLogRepository, times(1)).save(any(BackofficePushLog.class));
	}
}
