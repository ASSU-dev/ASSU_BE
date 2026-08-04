package com.assu.server.domain.qr.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;
import com.assu.server.domain.qr.entity.Qr;
import com.assu.server.domain.qr.entity.SortByMethod;
import com.assu.server.domain.qr.repository.TemporaryQrRepository;
import com.assu.server.domain.student.entity.StampEventApplicant;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StampEventApplicantRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class TemporaryQrServiceImplTest {

	@InjectMocks
	private TemporaryQrServiceImpl temporaryQrService;

	@Mock
	private TemporaryQrRepository temporaryQrRepository;

	@Mock
	private StudentRepository studentRepository;

	@Mock
	private StampEventApplicantRepository stampEventApplicantRepository;

	@Mock
	private NotificationCommandService notificationCommandService;

	private static final Long STUDENT_ID = 1L;

	private Member givenMember() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(STUDENT_ID);
		return member;
	}

	@Test
	@DisplayName("학생이 아닌 회원이 QR 인증을 하면 NO_SUCH_STUDENT 예외가 발생한다")
	void insertData_StudentNotFound_ThrowsException() {
		// 1. Given
		Member member = givenMember();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> temporaryQrService.insertData(new TemporaryQrRequestDTO("총학생회", SortByMethod.REVIEW), member));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_STUDENT, exception.getCode());
		verify(temporaryQrRepository, never()).save(any());
	}

	@Test
	@DisplayName("QR 인증 시 스탬프가 1 증가하고 QR 기록이 저장된다")
	void insertData_Success_IncreasesStampAndSavesQr() {
		// 1. Given (스탬프 5개인 학생 - 이벤트 조건 미달)
		Member member = givenMember();
		Student student = Student.builder().university(University.SSU).stamp(5).build();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		temporaryQrService.insertData(new TemporaryQrRequestDTO("총학생회", SortByMethod.REVIEW), member);

		// 3. Then
		assertEquals(6, student.getStamp());
		verify(stampEventApplicantRepository, never()).save(any());

		ArgumentCaptor<Qr> qrCaptor = ArgumentCaptor.forClass(Qr.class);
		verify(temporaryQrRepository, times(1)).save(qrCaptor.capture());
		assertEquals("총학생회", qrCaptor.getValue().getAdminName());
		assertEquals(STUDENT_ID, qrCaptor.getValue().getUserId());
		assertEquals(SortByMethod.REVIEW, qrCaptor.getValue().getSort());
	}

	@Test
	@DisplayName("스탬프가 10개가 되면 이벤트에 자동 응모되고 알림 전송 후 스탬프가 초기화된다")
	void insertData_TenthStamp_AppliesEventAndResetsStamp() {
		// 1. Given (스탬프 9개 → 이번 적립으로 10개)
		Member member = givenMember();
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).stamp(9).build();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		temporaryQrService.insertData(new TemporaryQrRequestDTO("총학생회", SortByMethod.SUGGEST), member);

		// 3. Then (이벤트 응모 + 알림 + 스탬프 초기화)
		ArgumentCaptor<StampEventApplicant> applicantCaptor = ArgumentCaptor.forClass(StampEventApplicant.class);
		verify(stampEventApplicantRepository, times(1)).save(applicantCaptor.capture());
		assertEquals(student, applicantCaptor.getValue().getStudent());
		assertEquals("2026_SEASON_1", applicantCaptor.getValue().getEventVersion());

		verify(notificationCommandService, times(1)).sendStamp(STUDENT_ID);
		assertEquals(0, student.getStamp());
	}

	@Test
	@DisplayName("스탬프 이벤트 알림 전송이 실패해도 스탬프 적립과 QR 저장은 성공한다")
	void insertData_NotificationFails_StillSavesQr() {
		// 1. Given
		Member member = givenMember();
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).stamp(9).build();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
		doThrow(new RuntimeException("FCM 오류")).when(notificationCommandService).sendStamp(STUDENT_ID);

		// 2. When
		assertDoesNotThrow(() ->
			temporaryQrService.insertData(new TemporaryQrRequestDTO("총학생회", SortByMethod.REVIEW), member));

		// 3. Then
		verify(temporaryQrRepository, times(1)).save(any(Qr.class));
		assertEquals(0, student.getStamp());
	}
}
