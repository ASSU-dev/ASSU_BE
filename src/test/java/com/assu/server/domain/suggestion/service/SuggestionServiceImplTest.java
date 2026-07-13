package com.assu.server.domain.suggestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.domain.suggestion.dto.GetSuggestionAdminsDTO;
import com.assu.server.domain.suggestion.dto.WriteSuggestionRequestDTO;
import com.assu.server.domain.suggestion.dto.WriteSuggestionResponseDTO;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.suggestion.repository.SuggestionRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceImplTest {

	@InjectMocks
	private SuggestionServiceImpl suggestionService;

	@Mock
	private SuggestionRepository suggestionRepository;

	@Mock
	private AdminRepository adminRepository;

	@Mock
	private StudentRepository studentRepository;

	@Mock
	private NotificationCommandService notificationCommandService;

	private static final Long STUDENT_ID = 1L;
	private static final Long ADMIN_ID = 10L;

	@Test
	@DisplayName("존재하지 않는 관리자에게 건의하면 NO_SUCH_ADMIN 예외가 발생한다")
	void writeSuggestion_AdminNotFound_ThrowsException() {
		// 1. Given
		WriteSuggestionRequestDTO request =
			new WriteSuggestionRequestDTO(ADMIN_ID, "스타벅스 숭실대점", "아메리카노 10% 할인");
		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> suggestionService.writeSuggestion(request, STUDENT_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_ADMIN, exception.getCode());
		verify(suggestionRepository, never()).save(any());
	}

	@Test
	@DisplayName("건의 작성 성공 시 건의가 저장되고 대상 관리자에게 알림이 전송된다")
	void writeSuggestion_Success_SavesAndNotifies() {
		// 1. Given
		WriteSuggestionRequestDTO request =
			new WriteSuggestionRequestDTO(ADMIN_ID, "스타벅스 숭실대점", "아메리카노 10% 할인");

		Admin admin = Admin.builder().id(ADMIN_ID).name("총학생회").isPhoneVerified(false).build();
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).build();
		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		WriteSuggestionResponseDTO response = suggestionService.writeSuggestion(request, STUDENT_ID);

		// 3. Then
		assertEquals(ADMIN_ID, response.adminId());
		assertEquals(STUDENT_ID, response.userId());
		assertEquals("스타벅스 숭실대점", response.storeName());
		assertEquals("아메리카노 10% 할인", response.suggestionBenefit());

		ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
		verify(suggestionRepository, times(1)).save(captor.capture());
		assertEquals(admin, captor.getValue().getAdmin());
		assertEquals(student, captor.getValue().getStudent());

		verify(notificationCommandService, times(1)).sendPartnerSuggestion(eq(ADMIN_ID), any());
	}

	@Test
	@DisplayName("존재하지 않는 학생이 건의 가능한 학생회를 조회하면 NO_SUCH_STUDENT 예외가 발생한다")
	void getSuggestionAdmins_StudentNotFound_ThrowsException() {
		// 1. Given
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> suggestionService.getSuggestionAdmins(STUDENT_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_STUDENT, exception.getCode());
	}

	@Test
	@DisplayName("매칭된 학생회를 총학·단과대·학부 단위로 분류하여 반환한다")
	void getSuggestionAdmins_ClassifiesAdminsByLevel() {
		// 1. Given (major만 있는 학부 학생회, department만 있는 단과대 학생회, 둘 다 없는 총학)
		Student student = Student.builder()
			.id(STUDENT_ID).university(University.SSU)
			.department(Department.IT).major(Major.COMPUTER_SCIENCE).build();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

		Admin universityAdmin = Admin.builder().id(1L).name("숭실대학교 총학생회").isPhoneVerified(false).build();
		Admin departmentAdmin = Admin.builder().id(2L).name("IT대학 학생회")
			.department(Department.IT).isPhoneVerified(false).build();
		Admin majorAdmin = Admin.builder().id(3L).name("컴퓨터학부 학생회")
			.department(Department.IT).major(Major.COMPUTER_SCIENCE).isPhoneVerified(false).build();
		when(adminRepository.findMatchingAdmins(University.SSU, Department.IT, Major.COMPUTER_SCIENCE))
			.thenReturn(List.of(universityAdmin, departmentAdmin, majorAdmin));

		// 2. When
		GetSuggestionAdminsDTO response = suggestionService.getSuggestionAdmins(STUDENT_ID);

		// 3. Then
		assertEquals(1L, response.adminId());
		assertEquals("숭실대학교 총학생회", response.adminName());
		assertEquals(2L, response.departId());
		assertEquals("IT대학 학생회", response.departName());
		assertEquals(3L, response.majorId());
		assertEquals("컴퓨터학부 학생회", response.majorName());
	}

	@Test
	@DisplayName("매칭된 학생회가 없으면 모든 필드가 null인 DTO를 반환한다")
	void getSuggestionAdmins_NoMatchingAdmins_ReturnsNullFields() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).build();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
		when(adminRepository.findMatchingAdmins(any(), any(), any())).thenReturn(List.of());

		// 2. When
		GetSuggestionAdminsDTO response = suggestionService.getSuggestionAdmins(STUDENT_ID);

		// 3. Then
		assertNull(response.adminId());
		assertNull(response.departId());
		assertNull(response.majorId());
	}
}
