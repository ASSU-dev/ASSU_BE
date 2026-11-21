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
import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;
import com.assu.server.domain.qr.entity.Qr;
import com.assu.server.domain.qr.entity.SortByMethod;
import com.assu.server.domain.qr.repository.TemporaryQrRepository;
import com.assu.server.domain.student.entity.Student;
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
	@DisplayName("QR 인증 시 QR 적립 기록이 저장된다")
	void insertData_Success_SavesQr() {
		// 1. Given
		Member member = givenMember();
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).build();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		temporaryQrService.insertData(new TemporaryQrRequestDTO("총학생회", SortByMethod.REVIEW), member);

		// 3. Then
		ArgumentCaptor<Qr> qrCaptor = ArgumentCaptor.forClass(Qr.class);
		verify(temporaryQrRepository, times(1)).save(qrCaptor.capture());
		assertEquals("총학생회", qrCaptor.getValue().getAdminName());
		assertEquals(STUDENT_ID, qrCaptor.getValue().getUserId());
		assertEquals(SortByMethod.REVIEW, qrCaptor.getValue().getSort());
	}
}
